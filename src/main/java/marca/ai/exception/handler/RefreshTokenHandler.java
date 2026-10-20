package marca.ai.exception.handler;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import marca.ai.exception.RefreshTokenException;
import marca.ai.exception.response.ErrorsResponse;

import java.util.List;

@Provider
public class RefreshTokenHandler implements ExceptionMapper<RefreshTokenException> {

    @Override
    public Response toResponse(RefreshTokenException exception) {
        return Response.status(exception.getRefreshTokenExceptionType().getStatus())
                .entity(new ErrorsResponse(List.of(exception.getMessage())))
                .build();
    }
}
