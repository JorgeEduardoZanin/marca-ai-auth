package marca.ai.service;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.pgclient.PgException;
import io.vertx.sqlclient.TransactionPropagation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import marca.ai.dto.request.CreateUserRequest;
import marca.ai.exception.mapper.ExceptionMapper;
import marca.ai.repository.CredentialRepository;
import marca.ai.repository.PoliciesRepository;
import marca.ai.repository.UserRepository;

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

    public UserService(Pool transactional, UserRepository userRepository, CredentialRepository credentialRepository, PoliciesRepository policiesRepository) {
        this.transactional = transactional;
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.policiesRepository = policiesRepository;
    }

    public Uni<UUID> createUser (CreateUserRequest request, String ip) {

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
                        .chain(userId -> credentialRepository.insertCredential(request, userId, encryptedPassword).replaceWith(userId))
                        .chain(userId -> policiesRepository.insertTermsAndPolicies(userId, request.privacyPolicyVersion(), request.termsOfUseVersion(), ip).replaceWith(userId))))
                .onFailure(PgException.class).transform(ExceptionMapper::fromPgException);
    }

    private Uni<String> hashPassword(String plain) {
        return Uni.createFrom().item(() -> BcryptUtil.bcryptHash(plain))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}
