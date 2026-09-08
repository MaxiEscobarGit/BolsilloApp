package bolsilloApp.api.shared.exception;

public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String resource) {
        super(resource + " not found", "NOT_FOUND", 404);
    }
}
