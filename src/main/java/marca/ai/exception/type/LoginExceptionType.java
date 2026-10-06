package marca.ai.exception.type;

import jakarta.ws.rs.core.Response;

public enum LoginExceptionType {

    EMAIL_INVALID_FORMAT("E-mail inválido.", Response.Status.BAD_REQUEST),
    EMAIL_MUST_NOT_EXCEED_254_CHARACTERS("E-mail não pode ter mais de 254 caracteres.", Response.Status.BAD_REQUEST),
    EMAIL_AND_PASSWORD_CANNOT_BE_NULL_OR_EMPTY("O e-mail e a senha não podem ser nulos nem vazios.", Response.Status.BAD_REQUEST),
    BLOCKED_USER("Acesso bloqueado até %1$td/%1$tm/%1$tY às %1$tHh%1$tM por sucessivas tentativas de login malsucedidas. Se você não reconhece essas tentativas, entre em contato com o suporte.", Response.Status.TOO_MANY_REQUESTS),
    BLOCKED_PERMANENTLY_USER("Acesso bloqueado permanentemente por sucessivas tentativas de login malsucedidas. Se você não reconhece essas tentativas, entre em contato com o suporte.", Response.Status.TOO_MANY_REQUESTS),
    EMAIL_HAS_NOT_BEEN_VERIFIED("O email ainda não foi verificado.", Response.Status.UNAUTHORIZED),
    INCORRECT_EMAIL_OR_PASSWORD("Email ou senha estão incorretos.", Response.Status.UNAUTHORIZED),
    INCORRECT_CODE("Código de verificação incorreto.", Response.Status.BAD_REQUEST),
    USER_NOT_FOUND("Usuário não encontrado",Response.Status.BAD_REQUEST),
    MAX_ATTEMPTS_MFA("Máximo de tentativas de login com mfa atingidos. Faça o login novamente.", Response.Status.BAD_REQUEST),
    MFA_CHALLENGE_NOT_FOUND("Verificação expirada ou já utilizada. Faça o login novamente.", Response.Status.UNAUTHORIZED);

    private final String message;

    private final Response.Status status;

    LoginExceptionType(String message, Response.Status status) {
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
