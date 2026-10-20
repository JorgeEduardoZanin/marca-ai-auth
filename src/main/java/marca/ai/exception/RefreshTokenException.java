package marca.ai.exception;

import marca.ai.exception.type.RefreshTokenExceptionType;

public class RefreshTokenException extends RuntimeException {

    private final RefreshTokenExceptionType refreshTokenExceptionType;

    public RefreshTokenException(RefreshTokenExceptionType refreshTokenExceptionType) {
        super(refreshTokenExceptionType.getMessage());
        this.refreshTokenExceptionType = refreshTokenExceptionType;
    }

    public RefreshTokenExceptionType getRefreshTokenExceptionType() {
        return refreshTokenExceptionType;
    }
}
