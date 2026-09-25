package marca.ai.service.client;


import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import marca.ai.dto.response.CEPResponse;
import marca.ai.exception.BusinessRuleException;
import marca.ai.exception.type.BusinessRuleExceptionType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/cep/v1")
@Produces(MediaType.APPLICATION_JSON)
@RegisterRestClient(configKey = "check-cep")
public interface CEPClient {

    @GET
    @Path("/{cep}")
    Uni<CEPResponse> findCep (@PathParam("cep") String cep);

    @ClientExceptionMapper
    static RuntimeException mapper (Response response) {
        if (response.getStatus() == 400) return new BusinessRuleException(BusinessRuleExceptionType.CEP_NOT_FOUND);
        return null;
    }
}
