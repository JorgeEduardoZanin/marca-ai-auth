package marca.ai.service.client;

import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import marca.ai.dto.response.CNPJResponse;
import marca.ai.exception.EnterpriseException;
import marca.ai.exception.ValidationException;
import marca.ai.exception.type.EnterpriseExceptionType;
import marca.ai.exception.type.ValidationExceptionType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;



@Path("/api/cnpj/v1")
@Produces(MediaType.APPLICATION_JSON)
@RegisterRestClient(configKey = "check-cnpj")
public interface CNPJClient {

    @GET
    @Path("/{cnpj}")
    Uni<CNPJResponse> findCNPJ (@PathParam("cnpj") String cnpj);

    @ClientExceptionMapper
    static RuntimeException mapper (Response response) {
        if (response.getStatus() == 404) return new EnterpriseException(EnterpriseExceptionType.CNPJ_NOT_FOUND);
        if (response.getStatus() == 400)  return new ValidationException(ValidationExceptionType.CPF_INVALID);
        return null;
    }
}
