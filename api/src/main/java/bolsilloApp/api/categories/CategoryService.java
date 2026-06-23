package bolsilloApp.api.categories;

import bolsilloApp.api.categories.dto.CategoryResponse;
import bolsilloApp.api.categories.dto.CreateCategoryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryResponse create(UUID userId, CreateCategoryRequest request) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<CategoryResponse> findAll(UUID userId) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public CategoryResponse findById(UUID userId, UUID id) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public CategoryResponse update(UUID userId, UUID id, CreateCategoryRequest request) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void delete(UUID userId, UUID id) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
