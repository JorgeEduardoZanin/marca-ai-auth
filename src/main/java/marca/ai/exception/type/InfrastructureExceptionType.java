package marca.ai.exception.type;

public enum InfrastructureExceptionType {

    UNKNOWN_INFRASTRUCTURE_ERROR("Erro desconhecido, contate o suporte");

    private final String message;

    InfrastructureExceptionType(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
