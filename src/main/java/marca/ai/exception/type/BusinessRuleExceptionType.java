package marca.ai.exception.type;

import jakarta.ws.rs.core.Response;

public enum BusinessRuleExceptionType {

    CEP_NOT_FOUND("Cep não encontrado ou inexistente.", Response.Status.NOT_FOUND),
    CPF_ALREADY_REGISTERED("CPF já cadastrado.", Response.Status.CONFLICT),
    EMAIL_ALREADY_REGISTERED("E-mail já cadastrado.", Response.Status.CONFLICT);

    private final String message;

    private final Response.Status status;

    BusinessRuleExceptionType(String message, Response.Status status) {
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
