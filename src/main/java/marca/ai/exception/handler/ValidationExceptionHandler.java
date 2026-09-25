package marca.ai.exception.handler;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import marca.ai.exception.ValidationException;
import marca.ai.exception.response.ErrorsResponse;

import java.util.ArrayList;
import java.util.List;

@Provider
public class ValidationExceptionHandler implements ExceptionMapper<ValidationException> {

    @Override
    public Response toResponse(ValidationException exception) {

        List<String> errors = new ArrayList<>(exception.getErrors().size());

        exception.getErrors()
                .forEach(failure -> errors.add(failure.getMessage()));

        return Response.status(Response.Status.BAD_REQUEST)
                       .entity(new ErrorsResponse(errors))
                       .build();
    }
}
