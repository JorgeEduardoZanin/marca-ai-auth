package marca.ai.controller;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import marca.ai.service.TokenService;

@ApplicationScoped
@Path("/.well-known/jwks.json")
public class JwksController {

    private final TokenService tokenService;

    public JwksController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> jwks () {

        return tokenService.jwks()
                .map(resp -> Response.status(Response.Status.OK)
                        .entity(resp)
                        .build());
    }
}
