package marca.ai.utils;

import org.apache.commons.codec.binary.Base32;

public final class StringBuilderUtils {

    private static final Base32 base32 = new Base32();

    public static String buildTotpUri (byte[] secretKey, String email) {

        return "otpauth://totp/MarcaAi:" +
                email +
                "?secret=" +
                base32.encodeAsString(secretKey) +
                "&issuer=MarcaAi&algorithm=SHA1&digits=6&period=30";
    }
}
