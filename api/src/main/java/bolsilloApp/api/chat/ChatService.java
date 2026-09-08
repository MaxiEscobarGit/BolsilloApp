package bolsilloApp.api.chat;

import bolsilloApp.api.auth.AuthRepository;
import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.categories.CategoryRepository;
import bolsilloApp.api.categories.model.Category;
import bolsilloApp.api.chat.dto.AiParsedResponse;
import bolsilloApp.api.chat.dto.ChatHistoryResponse;
import bolsilloApp.api.chat.dto.ChatRequest;
import bolsilloApp.api.chat.dto.ChatResponse;
import bolsilloApp.api.chat.model.ChatMessage;
import bolsilloApp.api.chat.model.UserAlias;
import bolsilloApp.api.metrics.dto.MetricsContext;
import bolsilloApp.api.shared.exception.ApiException;
import bolsilloApp.api.transactions.TransactionRepository;
import bolsilloApp.api.transactions.TransactionSource;
import bolsilloApp.api.transactions.TransactionType;
import bolsilloApp.api.transactions.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private static final int MAX_AI_MESSAGES_PER_MONTH = 100;

    private final AIService aiService;
    private final ContextBuilder contextBuilder;
    private final ChatMessageRepository chatMessageRepository;
    private final UserAliasRepository userAliasRepository;
    private final AuthRepository authRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public ChatResponse chat(UUID userId, ChatRequest request) {
        // 1. Check rate limit
        User user = authRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", "NOT_FOUND", 404));

        checkAndResetMonthlyQuota(user);

        if (user.getAiMessagesUsed() >= MAX_AI_MESSAGES_PER_MONTH) {
            throw new ApiException("Monthly AI message limit reached", "RATE_LIMITED", 429);
        }

        // 2. Save user message
        saveChatMessage(userId, "user", request.message(), null, null, null);

        // 3. Build context
        MetricsContext metricsContext = contextBuilder.buildMetricsContext(userId);
        List<Map<String, String>> aliases = contextBuilder.buildAliases(userId);

        // 4. Detect intent
        ChatIntent intent = detectIntent(request.message());

        // 5. Call AI
        AIService.AiResponse aiResponse = aiService.call(request.message(), intent, metricsContext, aliases);

        // 6. Process transaction if REGISTER intent
        Object data = null;
        UUID[] transactionIds = null;

        if (aiResponse.intent() == ChatIntent.REGISTER && aiResponse.transactionData() != null) {
            try {
                Transaction tx = processRegisterIntent(userId, aiResponse.transactionData());
                data = Map.of(
                        "transactionId", tx.getId(),
                        "amount", tx.getAmountCents() / 100.0,
                        "category", tx.getCategory().getName(),
                        "type", tx.getType().name()
                );
                transactionIds = new UUID[]{tx.getId()};
            } catch (Exception e) {
                log.warn("Failed to process REGISTER intent: {}", e.getMessage());
                // Return the AI message but without persisting the transaction
            }
        }

        // 7. Save assistant message
        saveChatMessage(userId, "assistant", aiResponse.message(), aiResponse.intent(),
                aiResponse.modelUsed(), aiResponse.tokensUsed());

        // 8. Decrement quota
        user.setAiMessagesUsed(user.getAiMessagesUsed() + 1);
        authRepository.save(user);

        return new ChatResponse(
                aiResponse.message(),
                aiResponse.intent(),
                data,
                aiResponse.modelUsed(),
                aiResponse.tokensUsed()
        );
    }

    public List<ChatHistoryResponse> getHistory(UUID userId, int limit, LocalDateTime before) {
        var pageable = PageRequest.of(0, Math.min(limit, 100));
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

    private ChatIntent detectIntent(String message) {
        String lower = message.toLowerCase().trim();

        // CORRECT intent patterns
        if (lower.contains("no era") || lower.contains("eso era") || lower.contains("correg")
                || lower.contains("en realidad") || lower.contains("quise decir")) {
            return ChatIntent.CORRECT;
        }

        // QUERY intent patterns
        if (lower.startsWith("?") || lower.startsWith("cuanto") || lower.startsWith("cuantos")
                || lower.startsWith("cuanta") || lower.startsWith("cuantas")
                || lower.contains("?") || lower.startsWith("que ")
                || lower.startsWith("como ") || lower.startsWith("donde ")
                || lower.startsWith("por que") || lower.startsWith("cual")) {
            return ChatIntent.QUERY;
        }

        // REGISTER intent patterns - has a number that could be an amount
        if (lower.matches(".*\\d+.*") && (lower.contains("$") || lower.contains("gaste")
                || lower.contains("pague") || lower.contains("cobre") || lower.contains("recibi")
                || lower.contains("compre") || lower.contains("gane")
                || lower.matches(".*\\d+[.,]?\\d*.*"))) {
            return ChatIntent.REGISTER;
        }

        return ChatIntent.CHAT;
    }

    private Transaction processRegisterIntent(UUID userId, AiParsedResponse.TransactionData txData) {
        // Validate amount
        if (txData.amount() == null || txData.amount().compareTo(BigDecimal.ZERO) <= 0
                || txData.amount().compareTo(BigDecimal.valueOf(1_000_000_000)) >= 0) {
            throw new ApiException("Invalid amount from AI", "INVALID_AI_DATA", 400);
        }

        // Validate and find category
        Category category = findCategoryByName(userId, txData.categoryName());

        // Validate date
        LocalDate date = txData.date() != null ? txData.date() : LocalDate.now();
        if (date.isAfter(LocalDate.now().plusDays(1))) {
            date = LocalDate.now();
        }

        // Determine type
        TransactionType type = TransactionType.EXPENSE;
        if (txData.type() != null) {
            try {
                type = TransactionType.valueOf(txData.type().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        Long amountCents = txData.amount()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        var transaction = Transaction.builder()
                .user(User.builder().id(userId).build())
                .category(category)
                .type(type)
                .amountCents(amountCents)
                .currency("ARS")
                .amountArsCents(amountCents)
                .exchangeRate(BigDecimal.ONE)
                .description(txData.description() != null ? txData.description() : "Registro via chat")
                .transactionDate(date)
                .source(TransactionSource.CHAT)
                .build();

        return transactionRepository.save(transaction);
    }

    private Category findCategoryByName(UUID userId, String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            // Default to "Otros" expense category
            return categoryRepository.findSystemOtrosByType(
                    bolsilloApp.api.categories.model.CategoryType.EXPENSE)
                    .orElseThrow(() -> new ApiException("Default category not found", "INTERNAL_ERROR", 500));
        }

        // Try to find by name (system or user's custom)
        return categoryRepository.findAllForUser(userId).stream()
                .filter(c -> c.getName().equalsIgnoreCase(categoryName))
                .findFirst()
                .orElseGet(() -> categoryRepository.findSystemOtrosByType(
                        bolsilloApp.api.categories.model.CategoryType.EXPENSE)
                        .orElseThrow(() -> new ApiException("Default category not found", "INTERNAL_ERROR", 500)));
    }

    private void saveChatMessage(UUID userId, String role, String content,
                                  ChatIntent intent, String modelUsed, Integer tokensUsed) {
        var msg = ChatMessage.builder()
                .user(User.builder().id(userId).build())
                .role(role)
                .content(content)
                .intent(intent)
                .modelUsed(modelUsed)
                .tokensUsed(tokensUsed)
                .build();
        chatMessageRepository.save(msg);
    }

    private void checkAndResetMonthlyQuota(User user) {
        if (user.getAiMessagesResetAt().plusMonths(1).isBefore(LocalDateTime.now())) {
            user.setAiMessagesUsed(0);
            user.setAiMessagesResetAt(LocalDateTime.now());
            authRepository.save(user);
        }
    }
}
