package marca.ai.service;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.ws.rs.core.Response;
import marca.ai.exception.InfrastructureException;
import marca.ai.exception.type.InfrastructureExceptionType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@ApplicationScoped
public class Aes256GcmService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";

    private static final String VERSION_PREFIX = "v1:";

    private static final int IV_LENGTH = 12;

    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey secretKey;

    private final SecureRandom random = new SecureRandom();

    public Aes256GcmService(@ConfigProperty(name = "auth.master-key") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) throw new InfrastructureException(InfrastructureExceptionType.UNKNOWN_INFRASTRUCTURE_ERROR, Response.Status.INTERNAL_SERVER_ERROR);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plainText, String AAD) {

        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(AAD.getBytes(StandardCharsets.UTF_8));

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + cipherText.length).put(iv).put(cipherText).array();

            return VERSION_PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            Log.errorf("Erro ao criptografar. erro=%s", e.getMessage());
            throw new InfrastructureException(InfrastructureExceptionType.UNKNOWN_INFRASTRUCTURE_ERROR, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    public String decrypt (String stored, String AAD) {
        if(!stored.startsWith(VERSION_PREFIX)) {
            Log.errorf("Versão de chave desconhecida.");
            throw new InfrastructureException(InfrastructureExceptionType.UNKNOWN_INFRASTRUCTURE_ERROR, Response.Status.INTERNAL_SERVER_ERROR);
        }

        try {
            ByteBuffer buf = ByteBuffer.wrap(Base64.getDecoder().decode(stored.substring(VERSION_PREFIX.length())));
            byte[] iv = new byte[IV_LENGTH];
            buf.get(iv);
            byte[] ciphertext = new byte[buf.remaining()];
            buf.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(AAD.getBytes(StandardCharsets.UTF_8));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            Log.errorf("Dado adulterado ou chave/AAD incorretos. erro=%s", e.getMessage());
            throw new InfrastructureException(InfrastructureExceptionType.UNKNOWN_INFRASTRUCTURE_ERROR, Response.Status.INTERNAL_SERVER_ERROR);
        } catch (GeneralSecurityException e) {
            Log.errorf("Falha ao descriptografar. erro=%s", e.getMessage());
            throw new InfrastructureException(InfrastructureExceptionType.UNKNOWN_INFRASTRUCTURE_ERROR, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

}
