package bolsilloApp.api.shared.exception;

public class BusinessRuleException extends ApiException {
    public BusinessRuleException(String message, String code) {
        super(message, code, 400);
    }
}
