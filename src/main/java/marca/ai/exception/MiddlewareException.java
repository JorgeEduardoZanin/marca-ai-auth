package marca.ai.exception;

import marca.ai.exception.type.MiddlewareExceptionType;

public class MiddlewareException extends RuntimeException{

    private final MiddlewareExceptionType middlewareExceptionType;

    public MiddlewareException(MiddlewareExceptionType middlewareExceptionType) {
        super (middlewareExceptionType.getMessage());
        this.middlewareExceptionType = middlewareExceptionType;
    }

    public MiddlewareExceptionType getMiddlewareExceptionType() {
        return middlewareExceptionType;
    }
}
