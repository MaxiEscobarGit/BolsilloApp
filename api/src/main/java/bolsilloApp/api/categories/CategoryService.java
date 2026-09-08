package bolsilloApp.api.categories;

import bolsilloApp.api.auth.model.User;
import bolsilloApp.api.categories.dto.CategoryResponse;
import bolsilloApp.api.categories.dto.CreateCategoryRequest;
import bolsilloApp.api.categories.model.Category;
import bolsilloApp.api.shared.exception.ApiException;
import bolsilloApp.api.transactions.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    public List<CategoryResponse> findAll(UUID userId) {
        return categoryRepository.findAllForUser(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public CategoryResponse create(UUID userId, CreateCategoryRequest request) {
        // Check for duplicate name within user's custom categories of same type
        if (categoryRepository.existsByUserIdAndNameIgnoreCaseAndType(userId, request.name(), request.type())) {
            throw new ApiException("Category with this name already exists", "CATEGORY_DUPLICATE", 409);
        }

        var category = Category.builder()
                .user(User.builder().id(userId).build())
                .name(request.name())
                .emoji(request.emoji())
                .colorHex(request.colorHex())
                .type(request.type())
                .isSystem(false)
                .build();
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(UUID userId, UUID categoryId) {
        var category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ApiException("Category not found", "NOT_FOUND", 404));

        if (category.getIsSystem()) {
            throw new ApiException("Cannot delete system category", "FORBIDDEN", 403);
        }

        if (category.getUser() == null || !category.getUser().getId().equals(userId)) {
            throw new ApiException("Category not found", "NOT_FOUND", 404);
        }

        // Check if category has active transactions
        boolean hasTransactions = transactionRepository.existsByCategoryIdAndDeletedAtIsNull(categoryId);
        if (hasTransactions) {
            // Reassign to "Otros" system category of the same type
            var otrosCategory = categoryRepository.findSystemOtrosByType(category.getType())
                    .orElseThrow(() -> new ApiException("System category 'Otros' not found", "INTERNAL_ERROR", 500));
            transactionRepository.reassignCategory(categoryId, otrosCategory.getId());
        }

        categoryRepository.delete(category);
    }

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getEmoji(), c.getColorHex(), c.getType(), c.getIsSystem());
    }
}
