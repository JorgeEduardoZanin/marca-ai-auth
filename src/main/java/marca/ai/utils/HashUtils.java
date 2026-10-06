package marca.ai.utils;

import io.quarkus.logging.Log;
import jakarta.ws.rs.core.Response;
import marca.ai.exception.InfrastructureException;
import marca.ai.exception.type.InfrastructureExceptionType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class HashUtils {

    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private HashUtils() {}

    /** Token opaco para desafios e links de uso unico. */
    public static String randomToken () {
        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /** SHA-256 em hex: 64 caracteres, exatamente o char(64) das colunas token_hash. */
    public static String sha256Hex (String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            Log.errorf("Erro ao gerar hash. erro=%s", e.getMessage());
            throw new InfrastructureException(InfrastructureExceptionType.UNKNOWN_INFRASTRUCTURE_ERROR, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
}
