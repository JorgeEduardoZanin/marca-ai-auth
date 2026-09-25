package marca.ai.exception;

import marca.ai.exception.type.EnterpriseExceptionType;

public class EnterpriseException extends RuntimeException{

    private final EnterpriseExceptionType enterpriseExceptionType;

    public EnterpriseException(EnterpriseExceptionType enterpriseExceptionType) {
        super(enterpriseExceptionType.getMessage());
        this.enterpriseExceptionType = enterpriseExceptionType;
    }

    public EnterpriseExceptionType getEnterpriseExceptionType() {
        return enterpriseExceptionType;
    }
}
