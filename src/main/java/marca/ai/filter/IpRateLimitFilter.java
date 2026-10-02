package marca.ai.filter;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import marca.ai.exception.MiddlewareException;
import marca.ai.exception.type.MiddlewareExceptionType;
import marca.ai.repository.redis.FailedAttemptsRepository;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import java.util.List;

@ApplicationScoped
public class IpRateLimitFilter {

    private static final int MAX_REQUESTS_PER_MINUTE = 120;

    private static final List<String> EXEMPT = List.of("/q/health", "/q/metrics", "/q/openapi");

    private final FailedAttemptsRepository failedAttemptsRepository;

    public IpRateLimitFilter(FailedAttemptsRepository failedAttemptsRepository) {
        this.failedAttemptsRepository = failedAttemptsRepository;
    }

    @ServerRequestFilter
    public Uni<Void> check(ContainerRequestContext ctx, RoutingContext routing) {

        String path = ctx.getUriInfo().getPath();
        if (EXEMPT.stream().anyMatch(path::startsWith)) return Uni.createFrom().nullItem();

        String ip = clientIp(routing);

        return failedAttemptsRepository.insertFailedAttemptsIP(ip)
                .onFailure().recoverWithItem(this::rateLimitUnavailable)
                .chain(attempts -> attempts > MAX_REQUESTS_PER_MINUTE
                        ? Uni.createFrom().failure(new MiddlewareException(MiddlewareExceptionType.MAX_REQUESTS_PER_MINUTE_EXCEEDED))
                        : Uni.createFrom().voidItem());
    }


    private Long rateLimitUnavailable(Throwable failure) {
        Log.errorf("Rate limit indisponivel, liberando requisicao. erro=%s", failure.getMessage());
        return 0L;
    }

    private String clientIp(RoutingContext routing) {
        return routing.request().remoteAddress().hostAddress();
    }
}
