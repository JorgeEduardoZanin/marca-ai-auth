package marca.ai.scheduler;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import io.vertx.pgclient.PgException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.ws.rs.core.Response;
import marca.ai.enums.ViolationCodes;
import marca.ai.exception.InfrastructureException;
import marca.ai.exception.type.InfrastructureExceptionType;
import marca.ai.repository.SignatureKeyRepository;
import marca.ai.service.Aes256GcmService;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class AuthKeyScheduler {

    private static final int RSA_KEY_SIZE = 2048;

    private static final int KID_SUFFIX_BYTES = 2;

    private static final long STARTUP_TIMEOUT_SECONDS = 30L;

    private final Aes256GcmService aes256GcmService;

    private final SignatureKeyRepository signatureKeyRepository;

    private final Vertx vertx;

    public AuthKeyScheduler(Aes256GcmService aes256GcmService, SignatureKeyRepository signatureKeyRepository, Vertx vertx) {
        this.aes256GcmService = aes256GcmService;
        this.signatureKeyRepository = signatureKeyRepository;
        this.vertx = vertx;
    }


    void onStart (@Observes StartupEvent ev) {

        CompletableFuture<Void> ready = new CompletableFuture<>();

        VertxContext.getOrCreateDuplicatedContext(vertx.getDelegate())
                .runOnContext(ignored -> signatureKeyRepository.existsActiveSignatureKey()
                        .chain(exists -> exists
                                ? Uni.createFrom().voidItem()
                                : generate())
                        .subscribe().with(ignore -> ready.complete(null), ready::completeExceptionally));

        try {
            ready.get(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Log.info("Chave de assinatura pronta.");
        } catch (Exception e) {
            Log.errorf("Falha ao preparar a chave de assinatura. erro=%s", e.getMessage());
            throw new IllegalStateException("Aplicacao nao pode subir sem chave de assinatura ativa", e);
        }
    }


    @Scheduled(cron = "0 0 4 ? * MON")
    public Uni<Void> generate () {

        KeyPair keyPair = generateKeyPair();
        String kid = buildKid();

        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String encryptedPrivateKey = aes256GcmService.encrypt(
                Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()), kid);

        return signatureKeyRepository.insertKeys(kid, publicKey, encryptedPrivateKey)
                .onFailure(PgException.class).recoverWithUni((PgException failure) -> {

                    if (ViolationCodes.UNIQUE.getCode().equals(failure.getSqlState())) {
                        Log.info("Outra instancia ja criou a chave ativa.");
                        return Uni.createFrom().voidItem();
                    }

                    return Uni.createFrom().failure(failure);
                });
    }

    private KeyPair generateKeyPair () {

        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(RSA_KEY_SIZE, SecureRandom.getInstanceStrong());

            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            Log.errorf("Erro ao gerar chaves de autenticação. erro=%s", e.getMessage());
            throw new InfrastructureException(InfrastructureExceptionType.UNKNOWN_INFRASTRUCTURE_ERROR, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Formato 2026-w40-6515: ano e semana ISO dizem de quando a chave e, e os
     * 2 bytes aleatorios evitam colisao na PK quando ha mais de uma geracao na
     * mesma semana (restart, rotacao manual apos incidente).
     */
    private String buildKid () {

        LocalDate today = LocalDate.now(Clock.systemUTC());
        WeekFields isoWeek = WeekFields.ISO;

        byte[] suffix = new byte[KID_SUFFIX_BYTES];

        try {
            SecureRandom.getInstanceStrong().nextBytes(suffix);
        } catch (NoSuchAlgorithmException e) {
            Log.errorf("Erro ao gerar o sufixo do kid. erro=%s", e.getMessage());
            throw new InfrastructureException(InfrastructureExceptionType.UNKNOWN_INFRASTRUCTURE_ERROR, Response.Status.INTERNAL_SERVER_ERROR);
        }

        return String.format("%d-w%02d-%s",
                today.get(isoWeek.weekBasedYear()),
                today.get(isoWeek.weekOfWeekBasedYear()),
                HexFormat.of().formatHex(suffix));
    }
}
