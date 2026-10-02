package marca.ai.exception.type;

import jakarta.ws.rs.core.Response;

public enum MiddlewareExceptionType {

    MAX_REQUESTS_PER_MINUTE_EXCEEDED("O limite máximo de requisições por minuto foi atingido. Tente novamente em 1 minuto.", Response.Status.TOO_MANY_REQUESTS);

    private final String message;

    private final Response.Status status;

    MiddlewareExceptionType(String message, Response.Status status) {
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
