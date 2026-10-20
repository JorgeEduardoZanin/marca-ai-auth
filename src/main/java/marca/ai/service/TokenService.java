package marca.ai.service;

import io.quarkus.logging.Log;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import marca.ai.dto.response.TokenResponse;
import marca.ai.exception.InfrastructureException;
import marca.ai.exception.type.InfrastructureExceptionType;
import marca.ai.model.JwksModel;
import marca.ai.model.RoleModel;
import marca.ai.repository.SignatureKeyRepository;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.RsaJsonWebKey;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;

@ApplicationScoped
public class TokenService {

    private static final String KEY_TYPE = "RSA";

    private static final String PUBLIC_KEY_USE = "sig";

    private static final String ALGORITHM = "RS256";

    private final Aes256GcmService aes256GcmService;

    private final SignatureKeyRepository signatureKeyRepository;

    private final long duration;

    public TokenService(SignatureKeyRepository signatureKeyRepository, @ConfigProperty(name = "auth.token.duration") long duration, Aes256GcmService aes256GcmService) {
        this.signatureKeyRepository = signatureKeyRepository;
        this.duration = duration;
        this.aes256GcmService = aes256GcmService;
    }

    public Uni<TokenResponse> generateToken (RoleModel model) {

        return signatureKeyRepository.findSignatureKey()
                .map(signatureKeyModel -> {
                    byte[] privateKeyBytes = Base64.getDecoder().decode(aes256GcmService.decrypt(signatureKeyModel.encryptedPrivateKey(), signatureKeyModel.kid()));

                    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privateKeyBytes);
                    PrivateKey privateKey;
                    try {
                        privateKey = KeyFactory.getInstance("RSA").generatePrivate(spec);
                    } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
                        Log.errorf("Erro ao assinar token. erro=%s", e.getMessage());
                        throw new InfrastructureException(InfrastructureExceptionType.UNKNOWN_INFRASTRUCTURE_ERROR, Response.Status.INTERNAL_SERVER_ERROR);
                    }

                    return new TokenResponse(Jwt.subject(model.ID().toString())
                            .upn(model.email())
                            .groups(model.role())
                            .claim("owner", model.owner())
                            .claim("employee", model.employee())
                            .expiresIn(Duration.ofSeconds(duration))
                            .jws()
                            .keyId(signatureKeyModel.kid())
                            .sign(privateKey), duration);
                });
    }


    public Uni<String> jwks() {
        return signatureKeyRepository.findPublicKey()
                .map(keys -> new JsonWebKeySet(
                        keys.stream().map(this::toJwk).toList()
                ).toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY));
    }


    public RSAPublicKey parsePublicKey (String base64PublicKey) {

        try {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(Base64.getDecoder().decode(base64PublicKey));

            return (RSAPublicKey) KeyFactory.getInstance(KEY_TYPE).generatePublic(spec);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            Log.errorf("Erro ao ler a chave publica do JWKS. erro=%s", e.getMessage());
            throw new InfrastructureException(InfrastructureExceptionType.UNKNOWN_INFRASTRUCTURE_ERROR, Response.Status.INTERNAL_SERVER_ERROR);
        }

    }

    private JsonWebKey toJwk (JwksModel model) {

        RsaJsonWebKey jwk = new RsaJsonWebKey(parsePublicKey(model.publicKey()));
        jwk.setKeyId(model.kid());
        jwk.setUse(PUBLIC_KEY_USE);
        jwk.setAlgorithm(ALGORITHM);

        return jwk;
    }
}
