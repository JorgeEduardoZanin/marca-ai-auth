package marca.ai.exception.handler;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import marca.ai.exception.InfrastructureException;
import marca.ai.exception.response.ErrorsResponse;

import java.util.List;

@Provider
public class InfrastructureErrorHandler implements ExceptionMapper<InfrastructureException> {

    @Override
    public Response toResponse(InfrastructureException exception) {
        return Response.status(exception.getStatus())
                .entity(new ErrorsResponse(List.of(exception.getMessage())))
                .build();
    }
}
