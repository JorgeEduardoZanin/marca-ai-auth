package marca.ai.controller;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import marca.ai.dto.request.CreateUserRequest;

@ApplicationScoped
@Path("/enterprise")
public class EnterpriseController {

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Void> createEnterprise (CreateUserRequest request) {

        return Uni.createFrom().voidItem();
    }
}
