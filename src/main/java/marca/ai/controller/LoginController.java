package marca.ai.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import marca.ai.dto.request.LoginRequest;
import marca.ai.dto.response.LoginResponse;
import marca.ai.service.LoginService;

@Path("/login")
@ApplicationScoped
public class LoginController {

    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }


    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginResponse(String uri, @JsonProperty("mfa_active") boolean mfaActive, @JsonProperty("expires_in") long expiresIn, String message) {}

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> login (LoginRequest request, RoutingContext routingContext) {
        String ip = routingContext.request().remoteAddress().hostAddress();
        return loginService.login(request, ip)
                .map(response -> Response.status(Response.Status.OK)
                        .entity(new LoginResponse(response.uri(), response.mfaActive(), response.expiresIn(), "Login concluído com sucesso!"))
                        .build());
    }
}
