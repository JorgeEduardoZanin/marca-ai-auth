package marca.ai.exception.handler;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import marca.ai.exception.LoginException;
import marca.ai.exception.response.ErrorsResponse;

import java.util.List;

@Provider
public class LoginExceptionHandler implements ExceptionMapper<LoginException> {


    @Override
    public Response toResponse(LoginException exception) {
        return Response.status(exception.getLoginExceptionType().getStatus())
                .entity(new ErrorsResponse(List.of(exception.getMessage())))
                .build();
    }
}
