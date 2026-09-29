package marca.ai.exception;

import marca.ai.exception.type.LoginExceptionType;

public class LoginException extends RuntimeException {

    private final LoginExceptionType loginExceptionType;

    public LoginException(LoginExceptionType loginExceptionType, Object... args) {
        super(loginExceptionType.getMessage().formatted(args));
        this.loginExceptionType = loginExceptionType;
    }

    public LoginExceptionType getLoginExceptionType() {
        return loginExceptionType;
    }
}
