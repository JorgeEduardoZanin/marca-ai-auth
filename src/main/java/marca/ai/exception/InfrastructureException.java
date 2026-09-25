package marca.ai.exception;

import jakarta.ws.rs.core.Response;
import marca.ai.exception.type.InfrastructureExceptionType;

public class InfrastructureException extends RuntimeException {

    private final InfrastructureExceptionType infrastructureExceptionType;

    private final Response.Status status;

    public InfrastructureException(InfrastructureExceptionType infrastructureExceptionType, Response.Status status) {
        super(infrastructureExceptionType.getMessage());
        this.infrastructureExceptionType = infrastructureExceptionType;
        this.status = status;
    }

    public InfrastructureExceptionType getInfrastructureExceptionType() {
        return infrastructureExceptionType;
    }

    public Response.Status getStatus() {
        return status;
    }
}
