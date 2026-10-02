package marca.ai.exception.handler;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import marca.ai.exception.MiddlewareException;
import marca.ai.exception.response.ErrorsResponse;

import java.util.List;

@Provider
public class MiddlewareExceptionHandler implements ExceptionMapper<MiddlewareException> {

    @Override
    public Response toResponse(MiddlewareException exception) {
        return Response.status(exception.getMiddlewareExceptionType().getStatus())
                .entity(new ErrorsResponse(List.of(exception.getMiddlewareExceptionType().getMessage())))
                .build();
    }
}
