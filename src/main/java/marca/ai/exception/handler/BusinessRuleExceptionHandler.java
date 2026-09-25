package marca.ai.exception.handler;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import marca.ai.exception.BusinessRuleException;
import marca.ai.exception.response.ErrorsResponse;

import java.util.List;

@Provider
public class BusinessRuleExceptionHandler implements ExceptionMapper<BusinessRuleException> {
    @Override
    public Response toResponse(BusinessRuleException exception) {
        return Response.status(exception.getBusinessRuleExceptionType().getStatus())
                .entity(new ErrorsResponse(List.of(exception.getMessage())))
                .build();
    }
}
