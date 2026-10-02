package marca.ai.service;

import io.quarkus.logging.Log;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import marca.ai.dto.response.TokenResponse;
import marca.ai.exception.InfrastructureException;
import marca.ai.exception.type.InfrastructureExceptionType;
import marca.ai.model.LoginModel;
import marca.ai.repository.SignatureKeyRepository;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;

@ApplicationScoped
public class TokenService {

    private final Aes256GcmService aes256GcmService;

    private final SignatureKeyRepository signatureKeyRepository;

    private final long duration;

    public TokenService(SignatureKeyRepository signatureKeyRepository, @ConfigProperty(name = "auth.token.duration") long duration, Aes256GcmService aes256GcmService) {
        this.signatureKeyRepository = signatureKeyRepository;
        this.duration = duration;
        this.aes256GcmService = aes256GcmService;
    }

    public Uni<TokenResponse> generateToken (LoginModel loginModel, String email) {

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

                    return new TokenResponse(Jwt.subject(loginModel.ID().toString())
                            .upn(email)
                            .groups(loginModel.role())
                            .claim("owner", loginModel.owner())
                            .claim("employee", loginModel.employee())
                            .expiresIn(Duration.ofMinutes(duration))
                            .sign(privateKey), duration, loginModel.mfaActive());
                });



    }
}
