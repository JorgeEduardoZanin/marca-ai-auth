package marca.ai.controller;

import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import marca.ai.dto.request.CreateUserRequest;
import marca.ai.service.UserService;

import java.util.UUID;

@Path("/user")
@ApplicationScoped
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    private record userResponse(UUID ID, String message, String totpUri){}

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> createUser (HttpServerRequest httpRequest, CreateUserRequest request) {

        return userService.createUser(request, httpRequest.remoteAddress().hostAddress())
                .map(userResponse -> Response.status(Response.Status.CREATED)
                        .entity(new userResponse(userResponse.id(), "Usuário criado com sucesso.", userResponse.totpUri()))
                        .build());
    }
}
