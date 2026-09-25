package marca.ai.exception.type;

import jakarta.ws.rs.core.Response;

public enum EnterpriseExceptionType {

    CNPJ_NOT_FOUND("Cnpj não encontrado ou inexistente.", Response.Status.NOT_FOUND);

    private final String message;

    private final Response.Status status;

    EnterpriseExceptionType(String message, Response.Status status) {
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
