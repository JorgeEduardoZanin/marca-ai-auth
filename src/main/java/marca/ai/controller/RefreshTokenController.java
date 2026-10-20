package marca.ai.controller;

import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import marca.ai.dto.request.RefreshTokenRequest;
import marca.ai.service.RefreshTokenService;

@Path("/refresh_token")
@ApplicationScoped
public class RefreshTokenController {

    private final RefreshTokenService refreshTokenService;

    public RefreshTokenController(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> refreshToken(RefreshTokenRequest request, RoutingContext routingContext) {

        return refreshTokenService.refreshToken(request.rawToken(), routingContext.request().remoteAddress().hostAddress(), routingContext.request().getHeader("User-Agent"))
                .map(response -> Response.status(Response.Status.OK)
                        .entity(response)
                        .build());
    }
}
