package marca.ai.service;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import marca.ai.dto.request.MFARequest;
import marca.ai.dto.response.TokenResponse;
import marca.ai.exception.InfrastructureException;
import marca.ai.exception.LoginException;
import marca.ai.exception.type.InfrastructureExceptionType;
import marca.ai.exception.type.LoginExceptionType;
import marca.ai.model.LoginModel;
import marca.ai.repository.AuthFactorRepository;
import marca.ai.repository.CredentialRepository;
import marca.ai.repository.MFAChallengeRepository;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;

@ApplicationScoped
public class MFAService {

    private static final int DIGITS = 6;

    private static final int MAX_ATTEMPTS_MFA = 3;

    /** Periodo do TOTP. Precisa casar com o period=30 da otpauth:// gerada no cadastro. */
    private static final long TOTP_PERIOD_SECONDS = 30L;

    /** Tolerancia de relogio: aceita o passo anterior e o seguinte. */
    private static final int TOTP_WINDOW_STEPS = 1;

    private final TimeBasedOneTimePasswordGenerator totp = new TimeBasedOneTimePasswordGenerator();

    private final CredentialRepository credentialRepository;

    private final Aes256GcmService aes256GcmService;

    private final TokenService tokenService;

    private final AuthFactorRepository authFactorRepository;

    private final MFAChallengeRepository mfaChallengeRepository;

    public MFAService(CredentialRepository credentialRepository, Aes256GcmService aes256GcmService, TokenService tokenService, AuthFactorRepository authFactorRepository, MFAChallengeRepository mfaChallengeRepository) {
        this.credentialRepository = credentialRepository;
        this.aes256GcmService = aes256GcmService;
        this.tokenService = tokenService;
        this.authFactorRepository = authFactorRepository;
        this.mfaChallengeRepository = mfaChallengeRepository;
    }

    public Uni<TokenResponse> mfa (MFARequest request) {

        return credentialRepository.login(request.ID())
                .chain(model -> {

                    if (model == null) {
                        Log.warnf("UUID=%s passou pela uri de mfa mesmo sem existir.", request.ID());
                        return Uni.createFrom().failure(new LoginException(LoginExceptionType.USER_NOT_FOUND));
                    }

                    return mfaChallengeRepository.findMFAChallenge(request.ID())
                            .chain(mfaModel -> {

                                if (mfaModel == null) return Uni.createFrom().failure(new LoginException(LoginExceptionType.MFA_CHALLENGE_NOT_FOUND));

                                if (mfaModel.attempts() >= MAX_ATTEMPTS_MFA) return Uni.createFrom().failure(new LoginException(LoginExceptionType.MAX_ATTEMPTS_MFA));

                                return matchStep(model, request.code())
                                        .chain(step -> {

                                            if (step == null) return mfaChallengeRepository.updateMFAChallenge(request.ID(), null, mfaModel.attempts() + 1)
                                                    .chain(() -> Uni.createFrom().failure(new LoginException(LoginExceptionType.INCORRECT_CODE)));

                                            return authFactorRepository.registerUse(model.ID(), step)
                                                    .chain(accepted -> {

                                                        if (!accepted) {
                                                            Log.warnf("UUID=%s reapresentou um codigo TOTP ja usado (passo=%d).", model.ID(), step);
                                                            return Uni.createFrom().failure(new LoginException(LoginExceptionType.INCORRECT_CODE));
                                                        }

                                                        return tokenService.generateToken(model, model.email())
                                                                .call(token -> mfaChallengeRepository.updateMFAChallenge(request.ID(), OffsetDateTime.now(), mfaModel.attempts()));
                                                    });
                                        });
                            });
                });
    }

    /**
     * Devolve o passo TOTP que bateu, ou null se nenhum da janela conferir.
     * Roda em worker thread: decifrar (AES-GCM) e gerar o codigo (HMAC) sao
     * trabalho de CPU e nao podem ocupar o event loop.
     */
    private Uni<Long> matchStep (LoginModel model, String code) {

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
