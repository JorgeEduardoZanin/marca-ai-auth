package marca.ai.service;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.sqlclient.TransactionPropagation;
import jakarta.enterprise.context.ApplicationScoped;
import marca.ai.dto.input.RefreshTokenInput;
import marca.ai.dto.response.RefreshTokenResponse;
import marca.ai.enums.ExpireDates;
import marca.ai.exception.RefreshTokenException;
import marca.ai.exception.type.RefreshTokenExceptionType;
import marca.ai.repository.RefreshTokenRepository;
import marca.ai.repository.RoleRepository;
import marca.ai.utils.HashUtils;

import java.time.OffsetDateTime;

@ApplicationScoped
public class RefreshTokenService {

    private final Pool pool;

    private final RefreshTokenRepository refreshTokenRepository;

    private final TokenService tokenService;

    private final RoleRepository roleRepository;

    public RefreshTokenService(Pool pool, RefreshTokenRepository refreshTokenRepository, TokenService tokenService, RoleRepository roleRepository) {
        this.pool = pool;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenService = tokenService;
        this.roleRepository = roleRepository;
    }

    public Uni<RefreshTokenResponse> refreshToken (String token, String ip, String userAgent) {

        return refreshTokenRepository.findRefreshToken(HashUtils.sha256Hex(token))
                .chain(model -> {

                    if (model == null) {
                        Log.warnf("Refresh token invalido ou inexistente apresentado. ip=%s userAgent=%s.", ip, userAgent);
                        return Uni.createFrom().failure(new RefreshTokenException(RefreshTokenExceptionType.REFRESH_TOKEN_EXPIRED));
                    }

                    if (model.usedIn() != null) {
                        Log.warnf("Usuário=%s tentou usar endpoint de refresh token com token já usado. ip=%s userAgent=%s.", model.userID(), ip, userAgent);
                        return refreshTokenRepository.revokesRefreshTokens(model.familyID())
                                .chain(() -> Uni.createFrom().failure(new RefreshTokenException(RefreshTokenExceptionType.REFRESH_TOKEN_ALREADY_USED)));
                    }

                    if (model.revokedOn() != null) {
                        Log.warnf("Usuário=%s tentou usar endpoint de refresh token com token revogado. ip=%s userAgent=%s.", model.userID(), ip, userAgent);
                        return refreshTokenRepository.revokesRefreshTokens(model.familyID())
                                .chain(() -> Uni.createFrom().failure(new RefreshTokenException(RefreshTokenExceptionType.REFRESH_TOKEN_ALREADY_USED)));
                    }

                    OffsetDateTime now = OffsetDateTime.now();
                    OffsetDateTime authAtPlusOneMonth =  model.authAt().plusMonths(ExpireDates.EXPIRE_IN_ONE_MONTH.getExpire());

                    if (model.expiresIn().isBefore(now) || (now.isAfter(authAtPlusOneMonth) || now.isEqual(authAtPlusOneMonth))) return refreshTokenRepository.revokesRefreshTokens(model.familyID())
                            .chain(() -> Uni.createFrom().failure(new RefreshTokenException(RefreshTokenExceptionType.REFRESH_TOKEN_EXPIRED)));

                    return roleRepository.findRole(model.userID())
                            .chain(role -> {

                                if (role == null) {
                                    Log.warnf("Usuário=%s não encontrado ao buscar papéis para o refresh.", model.userID());
                                    return Uni.createFrom().failure(new RefreshTokenException(RefreshTokenExceptionType.ROLE_NOT_FOUND));
                                }

                                return Uni.createFrom().item(role);
                            })
                            .chain(tokenService::generateToken)
                            .chain(tokenResponse -> {

                                String randomToken = HashUtils.randomToken();

                                return pool.withTransaction(TransactionPropagation.CONTEXT, tx -> refreshTokenRepository.insertRefreshToken(RefreshTokenInput.toRefreshTokenInput(
                                                model.userID(),
                                                model.familyID(),
                                                HashUtils.sha256Hex(randomToken),
                                                model.authAt(),
                                                ip,
                                                userAgent
                                        )).chain(() -> refreshTokenRepository.updatePreviousToken(HashUtils.sha256Hex(token))))
                                        .replaceWith(new RefreshTokenResponse(randomToken, tokenResponse.token(), tokenResponse.duration()));
                            });
                });
    }
}
