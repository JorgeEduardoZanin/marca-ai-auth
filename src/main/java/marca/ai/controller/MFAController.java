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
import marca.ai.dto.request.MFARequest;
import marca.ai.service.MFAService;

@Path("/mfa")
@ApplicationScoped
public class MFAController {

    private final MFAService mfaService;

    public MFAController(MFAService mfaService) {
        this.mfaService = mfaService;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> mfa (MFARequest request, RoutingContext routingContext) {

        String ip = routingContext.request().remoteAddress().hostAddress();
        String userAgent = routingContext.request().getHeader("User-Agent");

        return mfaService.mfa(request, userAgent, ip)
                .map(response -> Response.status(Response.Status.OK)
                        .entity(response)
                        .build());
    }
}
