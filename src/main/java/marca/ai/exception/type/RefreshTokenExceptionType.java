package marca.ai.exception.type;

import jakarta.ws.rs.core.Response;

public enum RefreshTokenExceptionType {

    REFRESH_TOKEN_ALREADY_USED("Refresh token expirado. Faça o login novamente.", Response.Status.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED("Refresh token expirado. Faça o login novamente.", Response.Status.UNAUTHORIZED),
    ROLE_NOT_FOUND("Usuário não encontrado.", Response.Status.NOT_FOUND);

    private final String message;

    private final Response.Status status;

    RefreshTokenExceptionType(String message, Response.Status status) {
        this.message = message;
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public Response.Status getStatus() {
        return status;
    }
}
