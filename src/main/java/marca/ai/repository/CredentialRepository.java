package marca.ai.repository;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.TransactionPropagation;
import jakarta.enterprise.context.ApplicationScoped;
import marca.ai.dto.request.CreateUserRequest;

import java.util.UUID;

@ApplicationScoped
public class CredentialRepository {

    private final Pool pool;

    public CredentialRepository(Pool pool) {
        this.pool = pool;
    }

    public Uni<Void> insertCredential(CreateUserRequest request, UUID uuid, String password) {

        String sql = """
                INSERT INTO marca_ai_auth.credencial (
                    usuario_id,
                    email,
                    senha_hash
                    ) VALUES ($1, $2, $3)
                """;

        Tuple params = Tuple.tuple()
                .addUUID(uuid)
                .addString(request.email())
                .addString(password);

        return pool.withTransaction(TransactionPropagation.CONTEXT, connection ->
                connection.preparedQuery(sql)
                        .execute(params)
                        .replaceWithVoid());

    }
}
