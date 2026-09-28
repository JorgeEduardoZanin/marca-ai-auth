package marca.ai.service;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.pgclient.PgException;
import io.vertx.sqlclient.TransactionPropagation;

import jakarta.enterprise.context.ApplicationScoped;

import jakarta.ws.rs.core.Response;
import marca.ai.dto.request.CreateUserRequest;
import marca.ai.dto.response.CreateUserResponse;
import marca.ai.exception.InfrastructureException;
import marca.ai.exception.mapper.ExceptionMapper;
import marca.ai.exception.type.InfrastructureExceptionType;
import marca.ai.repository.*;
import marca.ai.utils.StringBuilderUtils;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

@ApplicationScoped
public class UserService {

    /*
     * Nunca jogue a thread para um emitOn ou runSubscriptionOn, fazendo isso ela perde o contexto fazendo com que o
     * withTransaction não funcione e não de rollback em caso de erro de infra ou de Constraints.
     */
    private final Pool transactional;

    private final UserRepository userRepository;

    private final CredentialRepository credentialRepository;

    private final PoliciesRepository policiesRepository;

    private final UserRoleRepository userRoleRepository;

    private final Aes256GcmService aes256GcmService;

    private final AuthFactorRepository authFactorRepository;

    private final TimeBasedOneTimePasswordGenerator totp =  new TimeBasedOneTimePasswordGenerator();

    public UserService(Pool transactional, UserRepository userRepository, CredentialRepository credentialRepository, PoliciesRepository policiesRepository, UserRoleRepository userRoleRepository, Aes256GcmService aes256GcmService, AuthFactorRepository authFactorRepository) {
        this.transactional = transactional;
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.policiesRepository = policiesRepository;
        this.userRoleRepository = userRoleRepository;
        this.aes256GcmService = aes256GcmService;
        this.authFactorRepository = authFactorRepository;
    }

    public Uni<CreateUserResponse> createUser (CreateUserRequest request, String ip) {

        request.validate();
        request.validateContent();

        return hashPassword(request.password())
                /*
                 * CONTEXT é global, mas o armazenamento é local. Pense que cada requisição tem uma mochila (clone do contexto)
                 * e um callback (o que ela deve fazer quando voltar). Quando chega no I/O, a thread vai para a requisição B e deixa
                 * essas duas coisas da requisição A amarradas ao event loop. Quando o driver do PG responde, ela veste essa mochila
                 * e lê as instruções do que deve fazer. Assim a conexão fica guardada dentro dessa mochila, e toda operação
                 * usa a mesma conexão. Se o caminho feliz terminar, ela manda um COMMIT; se acontecer o caminho triste,
                 * manda um ROLLBACK.
                 */
                .chain(encryptedPassword -> transactional.withTransaction(TransactionPropagation.CONTEXT, tx -> userRepository.insertUser(request)
                        .chain(userID -> credentialRepository.insertCredential(request, userID, encryptedPassword).replaceWith(userID))
                        .chain( userID -> userRoleRepository.insertUserRole(userID).replaceWith(userID))
                        .chain(userID -> policiesRepository.insertTermsAndPolicies(userID, request.privacyPolicyVersion(), request.termsOfUseVersion(), ip).replaceWith(userID))
                        .chain(userID -> {

                            byte[] secret;

                            try {
                                final KeyGenerator keyGenerator = KeyGenerator.getInstance(totp.getAlgorithm());
                                keyGenerator.init(160);
                                SecretKey secretKey = keyGenerator.generateKey();
                                secret = secretKey.getEncoded();
                            } catch (NoSuchAlgorithmException e) {
                                Log.errorf("Erro ao gerar secret key do totp. erro=%s", e.getMessage());
                                throw new InfrastructureException(InfrastructureExceptionType.UNKNOWN_INFRASTRUCTURE_ERROR, Response.Status.INTERNAL_SERVER_ERROR);
                            }

                            String totpUri = StringBuilderUtils.buildTotpUri(secret, request.email());

                            return authFactorRepository.insertAuthFactor(userID, aes256GcmService.encrypt(Base64.getEncoder().encodeToString(secret), userID))
                                    .replaceWith(new CreateUserResponse(userID, totpUri));

                        })))
                .onFailure(PgException.class).transform(ExceptionMapper::fromPgException);
    }

    private Uni<String> hashPassword(String plain) {
        return Uni.createFrom().item(() -> BcryptUtil.bcryptHash(plain))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}
