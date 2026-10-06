package marca.ai.service;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import marca.ai.dto.request.LoginRequest;
import marca.ai.dto.response.LoginResponse;
import marca.ai.dto.response.TokenResponse;
import marca.ai.enums.MFAPurpose;
import marca.ai.exception.LoginException;
import marca.ai.exception.type.LoginExceptionType;
import marca.ai.repository.CredentialRepository;
import marca.ai.repository.MFAChallengeRepository;
import marca.ai.repository.redis.FailedAttemptsRepository;
import marca.ai.utils.CredentialUtils;
import java.util.Base64;
import marca.ai.utils.HashUtils;
import marca.ai.model.LoginModel;
import marca.ai.utils.StringBuilderUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

@ApplicationScoped
public class LoginService {

    private static final long MINIMAL_MARGIN_FOR_ERROR = 5;

    private static final int MAX_TENTATIVES_FALSE_EMAIL = 120;

    private static final int MFA_INACTIVE_DURATION_SECONDS = 600;

    private static final int MFA_ACTIVE_DURATION_SECONDS = 180;

    private final CredentialRepository credentialRepository;

    private final TokenService tokenService;

    private final FailedAttemptsRepository failedAttemptsRepository;

    private final Aes256GcmService aes256GcmService;

    private final MFAChallengeRepository mfaChallengeRepository;

    public LoginService(CredentialRepository credentialRepository, TokenService tokenService, FailedAttemptsRepository failedAttemptsRepository, Aes256GcmService aes256GcmService, MFAChallengeRepository mfaChallengeRepository) {
        this.credentialRepository = credentialRepository;
        this.tokenService = tokenService;
        this.failedAttemptsRepository = failedAttemptsRepository;
        this.aes256GcmService = aes256GcmService;
        this.mfaChallengeRepository = mfaChallengeRepository;
    }

    public Uni<LoginResponse> login (LoginRequest request, String ip) {

        request.validate();

        return failedAttemptsRepository.getBlocksIp(ip)
                .chain(exist -> exist == null
                        ? Uni.createFrom().voidItem()
                        : Uni.createFrom().failure(new LoginException(LoginExceptionType.INCORRECT_EMAIL_OR_PASSWORD)))
                .chain(() ->credentialRepository.login(request.email()))
                .chain(model -> {

                    if (model == null) return failedAttemptsRepository.insertFailedAttemptsFalseEmail(ip)
                            .chain(attempts -> {
                                if (attempts > MAX_TENTATIVES_FALSE_EMAIL) return failedAttemptsRepository.insertBlocksIP(ip)
                                        .invoke(blocks -> {
                                            if (blocks == 1) Log.warnf("IP=%s bloqueado por 24h após %d tentativas de login com emails inexistentes.", ip, attempts);
                                        });
                                return Uni.createFrom().voidItem();
                            })
                            .chain(() -> Uni.createFrom().failure(new LoginException(LoginExceptionType.INCORRECT_EMAIL_OR_PASSWORD)));

                    if (model.blockedUntil() != null && model.blockedUntil().isAfter(OffsetDateTime.now())) {
                        if (model.blockedUntil().isEqual(CredentialUtils.PERMANENT_BLOCK)) return Uni.createFrom().failure(new LoginException(LoginExceptionType.BLOCKED_PERMANENTLY_USER));
                        return Uni.createFrom().failure(new LoginException(LoginExceptionType.BLOCKED_USER, model.blockedUntil()));
                    }

                    if (model.emailVerifiedOn() == null) return Uni.createFrom().failure(new LoginException(LoginExceptionType.EMAIL_HAS_NOT_BEEN_VERIFIED));

                    return matches(request.password(), model.password())
                        .chain(matches -> {
                        if (!matches) {
                            return failedAttemptsRepository.insertFailedAttempts(request.email())
                                    .chain(attempts -> {
                                        if (attempts >= MINIMAL_MARGIN_FOR_ERROR) {
                                            OffsetDateTime blockedUntil = CredentialUtils.calculateBlockedUntil(attempts.intValue());

                                            if (blockedUntil != null){
                                                if (blockedUntil.isEqual(CredentialUtils.PERMANENT_BLOCK)) return credentialRepository.blocksUser(model.ID(), blockedUntil)
                                                        .chain(() -> Uni.createFrom().failure(new LoginException(LoginExceptionType.BLOCKED_PERMANENTLY_USER, blockedUntil)));

                                                return credentialRepository.blocksUser(model.ID(), blockedUntil)
                                                        .chain(() -> Uni.createFrom().failure(new LoginException(LoginExceptionType.BLOCKED_USER, blockedUntil)));
                                            }
                                        }
                                        return Uni.createFrom().failure(new LoginException(LoginExceptionType.INCORRECT_EMAIL_OR_PASSWORD));
                                    });
                        }
                        return failedAttemptsRepository.clearFaults(request.email()).replaceWith(model);
                    });
                })
                .chain(model -> {

                    int duration = model.mfaActive() ? MFA_ACTIVE_DURATION_SECONDS : MFA_INACTIVE_DURATION_SECONDS;

                    // O token JWT so nasce no /mfa, depois do codigo conferir. Aqui sai apenas
                    // o desafio: a prova de que a senha foi verificada agora.
                    return otpauthUri(model)
                            .chain(uri -> mfaChallengeRepository.insertMFAChallenge(
                                            model.ID(),
                                            MFAPurpose.LOGIN.getPurpose(),
                                            null,
                                            HashUtils.sha256Hex(HashUtils.randomToken()),
                                            OffsetDateTime.now().plusSeconds(duration))
                                    .replaceWith(new LoginResponse(model.ID(), uri, model.mfaActive(), duration)));
                });
    }

    /**
     * Devolve a otpauth:// para quem ainda nao verificou o fator, ou null se o MFA ja
     * esta ativo. Decifrar e AES-GCM: trabalho de CPU, nao pode ocupar o event loop.
     */
    private Uni<String> otpauthUri (LoginModel model) {

        if (model.mfaActive()) return Uni.createFrom().item(() -> (String) null);

        return Uni.createFrom().item(() -> StringBuilderUtils.buildTotpUri(
                        Base64.getDecoder().decode(aes256GcmService.decrypt(model.secretCipher(), model.ID().toString())),
                        model.email()))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private Uni<Boolean> matches(String plain, String hash) {
        return Uni.createFrom().item(() -> BcryptUtil.matches(plain, hash))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }


}
