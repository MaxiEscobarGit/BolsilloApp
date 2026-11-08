package bolsilloApp.api.chat;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.categories.model.Category;
import bolsilloApp.api.categories.model.CategoryType;
import bolsilloApp.api.chat.dto.AiParsedResponse;
import bolsilloApp.api.chat.dto.AiPromptContext;
import bolsilloApp.api.chat.dto.ChatHistoryResponse;
import bolsilloApp.api.chat.dto.ChatRequest;
import bolsilloApp.api.chat.dto.ChatResponse;
import bolsilloApp.api.chat.model.ChatMessage;
import bolsilloApp.api.shared.exception.ApiException;
import bolsilloApp.api.transactions.TransactionRepository;
import bolsilloApp.api.transactions.TransactionService;
import bolsilloApp.api.transactions.TransactionSource;
import bolsilloApp.api.transactions.TransactionType;
import bolsilloApp.api.transactions.dto.CreateTransactionRequest;
import bolsilloApp.api.transactions.dto.TransactionResponse;
import bolsilloApp.api.transactions.dto.UpdateTransactionRequest;
import bolsilloApp.api.transactions.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquesta el flujo del asistente:
 * <ol>
 *   <li>Lee el contexto (métricas, aliases, categorías) en una transacción read-only.</li>
 *   <li>Llama a Anthropic <b>fuera</b> de cualquier transacción de BD.</li>
 *   <li>Valida/prepara la acción (registro o corrección) sin tocar la BD.</li>
 *   <li>Abre una transacción corta para persistir mensajes, transacción, alias y cuota.</li>
 * </ol>
 */
@Service
@Slf4j
public class ChatService {

    static final int MAX_AI_MESSAGES_PER_MONTH = 100;
    static final String FALLBACK_EXPENSE_CATEGORY = "Otros";
    static final String FALLBACK_INCOME_CATEGORY = "Otros ingresos";

    private static final BigDecimal MAX_AMOUNT = BigDecimal.valueOf(1_000_000_000);
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_ALIAS_LENGTH = 200;

    private final AIService aiService;
    private final ContextBuilder contextBuilder;
    private final ChatMessageRepository chatMessageRepository;
    private final UserAliasRepository userAliasRepository;
    private final ChatQuotaRepository chatQuotaRepository;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;

    public ChatService(AIService aiService,
                       ContextBuilder contextBuilder,
                       ChatMessageRepository chatMessageRepository,
                       UserAliasRepository userAliasRepository,
                       ChatQuotaRepository chatQuotaRepository,
                       TransactionService transactionService,
                       TransactionRepository transactionRepository,
                       PlatformTransactionManager transactionManager) {
        this.aiService = aiService;
        this.contextBuilder = contextBuilder;
        this.chatMessageRepository = chatMessageRepository;
        this.userAliasRepository = userAliasRepository;
        this.chatQuotaRepository = chatQuotaRepository;
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(transactionManager);
    }

    public ChatResponse chat(UUID userId, ChatRequest request) {
        // 1. Quota: monthly reset + early check (no AI call if exhausted)
        LocalDateTime now = LocalDateTime.now();
        chatQuotaRepository.resetIfExpired(userId, now.minusMonths(1), now);
        int used = chatQuotaRepository.findAiMessagesUsed(userId)
                .orElseThrow(() -> new ApiException("User not found", "NOT_FOUND", 404));
        if (used >= MAX_AI_MESSAGES_PER_MONTH) {
            throw rateLimited();
        }

        // 2. Detect intent + read context (short read-only transaction)
        ChatIntent detectedIntent = IntentDetector.detect(request.message());
        ChatContext context = readTx.execute(status ->
                loadContext(userId, detectedIntent, request.transactionId()));

        // 3. Call the AI outside any DB transaction
        AIService.AiResponse aiResponse = aiService.call(request.message(), detectedIntent, context.prompt());

        // 4. Validate and prepare the action (reads only, no writes)
        PreparedAction action = prepareAction(userId, aiResponse, context);

        // 5. Persist everything atomically
        try {
            return writeTx.execute(status -> persist(userId, request.message(), aiResponse, action));
        } catch (ApiException e) {
            if ("RATE_LIMITED".equals(e.getCode())) {
                throw e;
            }
            log.warn("Chat action {} failed for user {}: {}", action.kind(), userId, e.getMessage());
            return writeTx.execute(status -> persist(userId, request.message(), aiResponse,
                    PreparedAction.failed(genericFailureMessage(action.kind()), "ACTION_FAILED")));
        } catch (RuntimeException e) {
            log.error("Chat action {} failed for user {}", action.kind(), userId, e);
            return writeTx.execute(status -> persist(userId, request.message(), aiResponse,
                    PreparedAction.failed(genericFailureMessage(action.kind()), "ACTION_FAILED")));
        }
    }

    public List<ChatHistoryResponse> getHistory(UUID userId, int limit, LocalDateTime before) {
        var pageable = PageRequest.of(0, Math.max(1, Math.min(limit, 100)));
        List<ChatMessage> messages;

        if (before != null) {
            messages = chatMessageRepository.findHistoryBefore(userId, before, pageable);
        } else {
            messages = chatMessageRepository.findRecentByUserId(userId, pageable);
        }

        return messages.stream()
                .map(m -> new ChatHistoryResponse(
                        m.getId(),
                        m.getRole(),
                        m.getContent(),
                        m.getIntent(),
                        m.getModelUsed(),
                        m.getTokensUsed(),
                        m.getCreatedAt()
                ))
                .toList();
    }

    // ---------------------------------------------------------------------
    // 2. Context
    // ---------------------------------------------------------------------

    private ChatContext loadContext(UUID userId, ChatIntent intent, UUID explicitTransactionId) {
        List<Category> categories = contextBuilder.buildCategories(userId);
        TransactionResponse correctionTarget = null;
        boolean targetLoaded = intent == ChatIntent.CORRECT || explicitTransactionId != null;
        if (targetLoaded) {
            correctionTarget = findCorrectionTarget(userId, explicitTransactionId).orElse(null);
        }

        AiPromptContext prompt = new AiPromptContext(
                contextBuilder.buildMetricsContext(userId),
                contextBuilder.buildAliases(userId),
                ContextBuilder.namesOfType(categories, CategoryType.EXPENSE),
                ContextBuilder.namesOfType(categories, CategoryType.INCOME),
                correctionTarget,
                LocalDate.now()
        );
        return new ChatContext(prompt, categories, explicitTransactionId, targetLoaded);
    }

    /**
     * The transaction to correct: the one indicated by the client (must belong to the user)
     * or, by default, the last non-deleted transaction created via chat.
     */
    private Optional<TransactionResponse> findCorrectionTarget(UUID userId, UUID explicitTransactionId) {
        if (explicitTransactionId != null) {
            // Throws 404 before calling the AI if it does not exist / is not the user's
            return Optional.of(transactionService.findById(userId, explicitTransactionId));
        }

        Specification<Transaction> lastChatTx = (root, query, cb) -> cb.and(
                cb.equal(root.get("user").get("id"), userId),
                cb.isNull(root.get("deletedAt")),
                cb.equal(root.get("source"), TransactionSource.CHAT));

        var page = transactionRepository.findAll(lastChatTx,
                PageRequest.of(0, 1, Sort.by(Sort.Order.desc("createdAt"))));

        return page.stream().findFirst()
                .map(t -> transactionService.findById(userId, t.getId()));
    }

    // ---------------------------------------------------------------------
    // 4. Prepare (validations, category and exchange rate resolution)
    // ---------------------------------------------------------------------

    private PreparedAction prepareAction(UUID userId, AIService.AiResponse ai, ChatContext context) {
        return switch (ai.intent()) {
            case REGISTER -> ai.transactionData() == null
                    ? PreparedAction.none()   // the AI is asking for missing data
                    : prepareRegister(userId, ai.transactionData(), context.categories());
            case CORRECT -> {
                TransactionResponse target = context.prompt().correctionTarget();
                if (!context.correctionTargetLoaded()) {
                    // The AI classified as CORRECT although rules did not: look up the target now
                    target = readTx.execute(s ->
                            findCorrectionTarget(userId, context.explicitTransactionId()).orElse(null));
                }
                yield prepareCorrect(userId, ai.transactionData(), target, context.categories());
            }
            case QUERY, CHAT -> PreparedAction.none();
        };
    }

    private PreparedAction prepareRegister(UUID userId, AiParsedResponse.TransactionData data,
                                           List<Category> categories) {
        if (!isValidAmount(data.amount())) {
            return PreparedAction.failed(
                    "No pude registrar el movimiento: no pude identificar un monto válido. "
                            + "¿Me lo repetís con el monto?", "INVALID_AI_DATA");
        }

        TransactionType type = parseType(data.type(), TransactionType.EXPENSE);
        Optional<Category> category = resolveCategory(categories, data.categoryName(), type)
                .or(() -> fallbackCategory(categories, type));
        if (category.isEmpty()) {
            return PreparedAction.failed(
                    "No pude registrar el movimiento: no encontré una categoría válida.", "INVALID_CATEGORY");
        }

        LocalDate date = clampDate(data.date() != null ? data.date() : LocalDate.now());
        String currency = normalizeCurrency(data.currency());
        if (currency == null) {
            currency = "ARS";
        }

        BigDecimal rate;
        try {
            rate = transactionService.resolveExchangeRate(currency, null, date, userId);
        } catch (ApiException e) {
            return exchangeRateFailure("registrar el movimiento", currency, date, e);
        }

        String description = data.description() != null ? data.description() : "Registro vía chat";

        return PreparedAction.register(new CreateTransactionRequest(
                data.amount(), type, truncate(description, MAX_DESCRIPTION_LENGTH),
                category.get().getId(), date, currency, rate));
    }

    private PreparedAction prepareCorrect(UUID userId, AiParsedResponse.TransactionData data,
                                          TransactionResponse target, List<Category> categories) {
        if (target == null) {
            return PreparedAction.failed(
                    "No encontré ningún movimiento registrado por chat para corregir.", "NOTHING_TO_CORRECT");
        }
        if (data == null) {
            return PreparedAction.failed(
                    "No pude aplicar la corrección: no entendí qué dato querés cambiar.", "INVALID_AI_DATA");
        }

        TransactionType newType = parseType(data.type(), null);
        TransactionType effectiveType = newType != null ? newType : target.type();

        // Category: explicit one from the AI, or a fallback if the type changed and no longer matches
        UUID categoryId = null;
        if (data.categoryName() != null) {
            Optional<Category> category = resolveCategory(categories, data.categoryName(), effectiveType);
            if (category.isEmpty()) {
                return PreparedAction.failed("No pude aplicar la corrección: no encontré la categoría \""
                        + data.categoryName() + "\".", "INVALID_CATEGORY");
            }
            categoryId = category.get().getId();
        } else if (newType != null && newType != target.type()) {
            Optional<Category> fallback = fallbackCategory(categories, newType);
            if (fallback.isEmpty()) {
                return PreparedAction.failed(
                        "No pude aplicar la corrección: no encontré una categoría válida.", "INVALID_CATEGORY");
            }
            categoryId = fallback.get().getId();
        }

        if (data.amount() != null && !isValidAmount(data.amount())) {
            return PreparedAction.failed(
                    "No pude aplicar la corrección: el monto no es válido.", "INVALID_AI_DATA");
        }

        String currency = normalizeCurrency(data.currency());
        LocalDate date = data.date() != null ? clampDate(data.date()) : null;

        BigDecimal rate = null;
        if (data.amount() != null || currency != null || date != null) {
            String effectiveCurrency = currency != null ? currency : target.currency();
            LocalDate effectiveDate = date != null ? date : target.transactionDate();
            try {
                rate = transactionService.resolveExchangeRate(effectiveCurrency, null, effectiveDate, userId);
            } catch (ApiException e) {
                return exchangeRateFailure("aplicar la corrección", effectiveCurrency, effectiveDate, e);
            }
        }

        String description = data.description() != null
                ? truncate(data.description(), MAX_DESCRIPTION_LENGTH) : null;

        boolean changesSomething = newType != null || categoryId != null || data.amount() != null
                || currency != null || date != null || description != null;
        if (!changesSomething) {
            return PreparedAction.failed(
                    "No pude aplicar la corrección: no entendí qué dato querés cambiar.", "INVALID_AI_DATA");
        }

        // Alias learning: the term the user used, or the original description when the category changed
        String alias = normalizeAlias(data.alias());
        if (alias == null && categoryId != null) {
            alias = normalizeAlias(target.description());
        }
        UUID aliasCategoryId = categoryId != null ? categoryId : target.categoryId();

        var update = new UpdateTransactionRequest(
                data.amount(), newType, description, categoryId, date, currency, rate);
        return PreparedAction.correct(target.id(), update, alias, aliasCategoryId);
    }

    // ---------------------------------------------------------------------
    // 5. Persist (single short transaction)
    // ---------------------------------------------------------------------

    private ChatResponse persist(UUID userId, String userMessage, AIService.AiResponse ai,
                                 PreparedAction action) {
        if (chatQuotaRepository.tryConsume(userId, MAX_AI_MESSAGES_PER_MONTH) == 0) {
            throw rateLimited();
        }

        saveChatMessage(userId, "user", userMessage, null, null, null, null);

        String reply = ai.message();
        Map<String, Object> data = null;
        UUID[] transactionsCreated = null;

        switch (action.kind()) {
            case REGISTER -> {
                TransactionResponse tx = transactionService.create(
                        userId, action.createRequest(), TransactionSource.CHAT);
                data = transactionData(tx);
                transactionsCreated = new UUID[]{tx.id()};
            }
            case CORRECT -> {
                TransactionResponse tx = transactionService.update(
                        userId, action.targetTransactionId(), action.updateRequest());
                data = transactionData(tx);
                if (action.alias() != null) {
                    userAliasRepository.upsert(userId, action.alias(), action.aliasCategoryId());
                    data.put("alias", action.alias());
                }
            }
            case FAILED -> {
                reply = action.failureMessage();
                data = new LinkedHashMap<>();
                data.put("error", action.failureCode());
            }
            case NONE -> { }
        }

        saveChatMessage(userId, "assistant", reply, ai.intent(), ai.modelUsed(), ai.tokensUsed(),
                transactionsCreated);

        return new ChatResponse(reply, ai.intent(), data, ai.modelUsed(), ai.tokensUsed());
    }

    private Map<String, Object> transactionData(TransactionResponse tx) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("transactionId", tx.id());
        data.put("amount", tx.amount());
        data.put("currency", tx.currency());
        data.put("category", tx.categoryName());
        data.put("type", tx.type().name());
        data.put("date", tx.transactionDate());
        return data;
    }

    private void saveChatMessage(UUID userId, String role, String content, ChatIntent intent,
                                 String modelUsed, Integer tokensUsed, UUID[] transactionsCreated) {
        var msg = ChatMessage.builder()
                .user(User.builder().id(userId).build())
                .role(role)
                .content(content)
                .intent(intent)
                .modelUsed(modelUsed)
                .tokensUsed(tokensUsed)
                .transactionsCreated(transactionsCreated)
                .build();
        chatMessageRepository.save(msg);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static ApiException rateLimited() {
        return new ApiException("Monthly AI message limit reached", "RATE_LIMITED", 429);
    }

    private static String genericFailureMessage(PreparedAction.Kind kind) {
        return kind == PreparedAction.Kind.CORRECT
                ? "No pude aplicar la corrección por un error interno. Probá de nuevo en un momento."
                : "No pude registrar el movimiento por un error interno. Probá de nuevo en un momento.";
    }

    private static PreparedAction exchangeRateFailure(String what, String currency, LocalDate date,
                                                      ApiException e) {
        if ("MISSING_EXCHANGE_RATE".equals(e.getCode())) {
            return PreparedAction.failed("No pude " + what + ": no tengo la cotización de " + currency
                    + " para el " + date + ". Cargala o indicá el monto en pesos.", "MISSING_EXCHANGE_RATE");
        }
        return PreparedAction.failed("No pude " + what + ".", e.getCode());
    }

    private static boolean isValidAmount(BigDecimal amount) {
        return amount != null
                && amount.compareTo(BigDecimal.ZERO) > 0
                && amount.compareTo(MAX_AMOUNT) < 0;
    }

    private static TransactionType parseType(String raw, TransactionType defaultType) {
        if (raw == null) {
            return defaultType;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "INCOME", "INGRESO" -> TransactionType.INCOME;
            case "EXPENSE", "GASTO" -> TransactionType.EXPENSE;
            default -> defaultType;
        };
    }

    private static CategoryType categoryTypeOf(TransactionType type) {
        return type == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
    }

    /** Category with that name AND matching type; user's custom ones take precedence over system ones. */
    private static Optional<Category> resolveCategory(List<Category> categories, String name,
                                                      TransactionType type) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String wanted = IntentDetector.normalize(name);
        CategoryType categoryType = categoryTypeOf(type);
        return categories.stream()
                .filter(c -> c.getType() == categoryType)
                .filter(c -> IntentDetector.normalize(c.getName()).equals(wanted))
                .min((a, b) -> Boolean.compare(Boolean.TRUE.equals(a.getIsSystem()),
                        Boolean.TRUE.equals(b.getIsSystem())));
    }

    /** INCOME → "Otros ingresos", EXPENSE → "Otros" (system categories). */
    private static Optional<Category> fallbackCategory(List<Category> categories, TransactionType type) {
        CategoryType categoryType = categoryTypeOf(type);
        String name = categoryType == CategoryType.INCOME ? FALLBACK_INCOME_CATEGORY : FALLBACK_EXPENSE_CATEGORY;
        return categories.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsSystem()))
                .filter(c -> c.getType() == categoryType)
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    private static LocalDate clampDate(LocalDate date) {
        LocalDate today = LocalDate.now();
        return date.isAfter(today.plusDays(1)) ? today : date;
    }

    private static String normalizeCurrency(String raw) {
        if (raw == null) {
            return null;
        }
        String c = raw.trim().toUpperCase(Locale.ROOT);
        return switch (c) {
            case "$", "PESOS", "PESO" -> "ARS";
            case "US$", "U$S", "DOLARES", "DOLAR", "DÓLARES", "DÓLAR" -> "USD";
            case "EUROS", "EURO", "€" -> "EUR";
            default -> c.matches("[A-Z]{3}") ? c : null;
        };
    }

    private static String normalizeAlias(String raw) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        return truncate(raw.trim().toLowerCase(Locale.ROOT), MAX_ALIAS_LENGTH);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ---------------------------------------------------------------------
    // Internal records
    // ---------------------------------------------------------------------

    private record ChatContext(AiPromptContext prompt, List<Category> categories,
                               UUID explicitTransactionId, boolean correctionTargetLoaded) {}

    record PreparedAction(
            Kind kind,
            CreateTransactionRequest createRequest,
            UUID targetTransactionId,
            UpdateTransactionRequest updateRequest,
            String alias,
            UUID aliasCategoryId,
            String failureMessage,
            String failureCode
    ) {
        enum Kind { NONE, REGISTER, CORRECT, FAILED }

        static PreparedAction none() {
            return new PreparedAction(Kind.NONE, null, null, null, null, null, null, null);
        }

        static PreparedAction register(CreateTransactionRequest request) {
            return new PreparedAction(Kind.REGISTER, request, null, null, null, null, null, null);
        }

        static PreparedAction correct(UUID targetId, UpdateTransactionRequest update,
                                      String alias, UUID aliasCategoryId) {
            return new PreparedAction(Kind.CORRECT, null, Objects.requireNonNull(targetId), update,
                    alias, aliasCategoryId, null, null);
        }

        static PreparedAction failed(String message, String code) {
            return new PreparedAction(Kind.FAILED, null, null, null, null, null, message, code);
        }
    }
}
