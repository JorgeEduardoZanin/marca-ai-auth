package marca.ai.service;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import marca.ai.dto.request.LoginRequest;
import marca.ai.dto.response.LoginResponse;
import marca.ai.exception.LoginException;
import marca.ai.exception.type.LoginExceptionType;
import marca.ai.model.LoginModel;
import marca.ai.repository.LoginRepository;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.LocalDateTime;

@ApplicationScoped
public class LoginService {

    private final LoginRepository loginRepository;

    private final long duration;

    public LoginService(LoginRepository loginRepository, @ConfigProperty(name = "auth.token.duration") long duration) {
        this.loginRepository = loginRepository;
        this.duration = duration;
    }

    public Uni<LoginResponse> login (LoginRequest request) {

        request.validate();

        return loginRepository.login(request.email())
                .chain(model -> {

                    if (model == null) return Uni.createFrom().failure(new LoginException(LoginExceptionType.INCORRECT_EMAIL_OR_PASSWORD));

                    if (model.blockedUntil() != null && model.blockedUntil().isAfter(LocalDateTime.now())) return Uni.createFrom().failure(new LoginException(LoginExceptionType.BLOCKED_USER, model.blockedUntil()));

                    if (model.emailVerifiedOn() == null) return Uni.createFrom().failure(new LoginException(LoginExceptionType.EMAIL_HAS_NOT_BEEN_VERIFIED));

                    return matches(request.password(), model.password())
                            .map(matches -> {
                                if (!matches) throw new LoginException(LoginExceptionType.INCORRECT_EMAIL_OR_PASSWORD);
                                return model;
                            });
                })
                .map(model -> LoginResponse.bearer(
                        generateToken(model, request.email()),
                        duration));
    }


    public String generateToken (LoginModel model, String email) {

        return Jwt.subject(model.id().toString())
                .upn(email)
                .groups(model.role())
                .claim("owner", model.owner())
                .claim("employee", model.employee())
                .expiresIn(Duration.ofMinutes(duration))
                .sign();
    }

    private Uni<Boolean> matches(String plain, String hash) {
        return Uni.createFrom().item(() -> BcryptUtil.matches(plain, hash))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private Uni<String> hashPassword(String plain) {
        return Uni.createFrom().item(() -> BcryptUtil.bcryptHash(plain))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}
