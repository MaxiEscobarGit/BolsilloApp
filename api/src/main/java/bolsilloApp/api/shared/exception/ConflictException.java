package bolsilloApp.api.shared.exception;

public class ConflictException extends ApiException {
    public ConflictException(String message, String code) {
        super(message, code, 409);
    }
}
