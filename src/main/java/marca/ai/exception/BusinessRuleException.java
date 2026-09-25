package marca.ai.exception;

import marca.ai.exception.type.BusinessRuleExceptionType;

public class BusinessRuleException extends RuntimeException {

    private final BusinessRuleExceptionType businessRuleExceptionType;

    public BusinessRuleException(BusinessRuleExceptionType businessRuleExceptionType) {
        super(businessRuleExceptionType.getMessage());
        this.businessRuleExceptionType = businessRuleExceptionType;
    }

    public BusinessRuleExceptionType getBusinessRuleExceptionType() {
        return businessRuleExceptionType;
    }
}
