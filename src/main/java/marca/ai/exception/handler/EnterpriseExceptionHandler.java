package marca.ai.exception.handler;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import marca.ai.exception.EnterpriseException;
import marca.ai.exception.response.ErrorsResponse;

import java.util.List;

@Provider
public class EnterpriseExceptionHandler implements ExceptionMapper<EnterpriseException> {
    @Override
    public Response toResponse(EnterpriseException exception) {
        return Response.status(exception.getEnterpriseExceptionType().getStatus())
                .entity(new ErrorsResponse(List.of(exception.getMessage())))
                .build();
    }
}
