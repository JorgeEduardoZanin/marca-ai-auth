package marca.ai.service;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.sqlclient.TransactionPropagation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import marca.ai.dto.input.RefreshTokenInput;
import marca.ai.dto.request.MFARequest;
import marca.ai.dto.response.MFAResponse;
import marca.ai.exception.InfrastructureException;
import marca.ai.exception.LoginException;
import marca.ai.exception.type.InfrastructureExceptionType;
import marca.ai.exception.type.LoginExceptionType;
import marca.ai.model.MFAModel;
import marca.ai.repository.AuthFactorRepository;
import marca.ai.repository.MFAChallengeRepository;
import marca.ai.repository.RefreshTokenRepository;
import marca.ai.repository.RoleRepository;
import marca.ai.utils.HashUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

@ApplicationScoped
public class MFAService {

    private static final int DIGITS = 6;

    private static final int MAX_ATTEMPTS_MFA = 3;

    /** Periodo do TOTP. Precisa casar com o period=30 da otpauth:// gerada no cadastro. */
    private static final long TOTP_PERIOD_SECONDS = 30L;

    /** Tolerancia de relogio: aceita o passo anterior e o seguinte. */
    private static final int TOTP_WINDOW_STEPS = 1;

    private final TimeBasedOneTimePasswordGenerator totp = new TimeBasedOneTimePasswordGenerator();

    private final Pool pool;

    private final Aes256GcmService aes256GcmService;

    private final TokenService tokenService;

    private final AuthFactorRepository authFactorRepository;

    private final MFAChallengeRepository mfaChallengeRepository;

    private final RefreshTokenRepository refreshTokenRepository;

    private final RoleRepository roleRepository;

    public MFAService(Pool pool, Aes256GcmService aes256GcmService, TokenService tokenService, AuthFactorRepository authFactorRepository, MFAChallengeRepository mfaChallengeRepository, RefreshTokenRepository refreshTokenRepository, RoleRepository roleRepository) {
        this.pool = pool;
        this.aes256GcmService = aes256GcmService;
        this.tokenService = tokenService;
        this.authFactorRepository = authFactorRepository;
        this.mfaChallengeRepository = mfaChallengeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.roleRepository = roleRepository;
    }

    public Uni<MFAResponse> mfa (MFARequest request, String userAgent, String ip) {

        String hashToken = HashUtils.sha256Hex(request.rawToken());

        return mfaChallengeRepository.findMFAChallenge(hashToken)
                .chain(model -> {

                    if (model == null) {
                        Log.warnf("Token de desafio MFA invalido, expirado ou ja utilizado.");
                        return Uni.createFrom().failure(new LoginException(LoginExceptionType.MFA_CHALLENGE_NOT_FOUND));
                    }

                    if (model.attempts() >= MAX_ATTEMPTS_MFA) return Uni.createFrom().failure(new LoginException(LoginExceptionType.MAX_ATTEMPTS_MFA));

                    return matchStep(model, request.code())
                            .chain(step -> {

                                if (step == null) return mfaChallengeRepository.updateMFAChallenge(hashToken, null, model.attempts() + 1)
                                        .chain(() -> Uni.createFrom().failure(new LoginException(LoginExceptionType.INCORRECT_CODE)));

                                return authFactorRepository.registerUse(model.ID(), step)
                                        .chain(accepted -> {

                                            if (!accepted) {
                                                Log.warnf("UUID=%s reapresentou um codigo TOTP ja usado (passo=%d).", model.ID(), step);
                                                return Uni.createFrom().failure(new LoginException(LoginExceptionType.INCORRECT_CODE));
                                            }

                                            return roleRepository.findRole(model.ID())
                                                    .chain(tokenService::generateToken)
                                                    .chain(token -> {

                                                        String randomToken = HashUtils.randomToken();

                                                        return pool.withTransaction(TransactionPropagation.CONTEXT, tx -> mfaChallengeRepository.updateMFAChallenge(hashToken, OffsetDateTime.now(), model.attempts())
                                                                        .chain(() -> refreshTokenRepository.insertRefreshToken(RefreshTokenInput.toRefreshTokenInput(
                                                                                model.ID(),
                                                                                UUID.randomUUID(),
                                                                                HashUtils.sha256Hex(randomToken),
                                                                                OffsetDateTime.now(),
                                                                                ip,
                                                                                userAgent
                                                                        ))))
                                                                .replaceWith(MFAResponse.toMFAResponse(randomToken, token));
                                                    });
                                        });
                            });
                });
    }

    private Uni<Long> matchStep (MFAModel model, String code) {

        return Uni.createFrom().item(() -> {
            byte[] secret = Base64.getDecoder().decode(
                    aes256GcmService.decrypt(model.secretCipher(), model.ID().toString()));

            SecretKey key = new SecretKeySpec(secret, totp.getAlgorithm());
            Instant now = Instant.now();

            try {
                for (int offset = -TOTP_WINDOW_STEPS; offset <= TOTP_WINDOW_STEPS; offset++) {

                    Instant at = now.plusSeconds(offset * TOTP_PERIOD_SECONDS);

                    String expected = String.format("%0" + DIGITS + "d", totp.generateOneTimePassword(key, at));

                    if (expected.equals(code)) return at.getEpochSecond() / TOTP_PERIOD_SECONDS;
                }

                return null;

            } catch (InvalidKeyException e) {
                Log.errorf("Erro ao calcular totp. erro=%s", e.getMessage());
                throw new InfrastructureException(InfrastructureExceptionType.UNKNOWN_INFRASTRUCTURE_ERROR, Response.Status.INTERNAL_SERVER_ERROR);
            } finally {
                Arrays.fill(secret, (byte) 0);
            }

        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}
