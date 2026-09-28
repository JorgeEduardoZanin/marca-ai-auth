package marca.ai.repository;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.TransactionPropagation;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class AuthFactorRepository {

    private final Pool pool;

    public AuthFactorRepository(Pool pool) {
        this.pool = pool;
    }

    public Uni<Void> insertAuthFactor(UUID ID, String secretCipher) {

        String sql = """
                INSERT INTO marca_ai_auth.fator_autenticacao (
                    usuario_id,
                    segredo_cifrado
                    ) VALUES ($1, $2)
                """;

        Tuple params = Tuple.tuple()
                .addUUID(ID)
                .addString(secretCipher);

        return pool.withTransaction(TransactionPropagation.CONTEXT, connection ->
                connection.preparedQuery(sql)
                        .execute(params)
                        .replaceWithVoid());
    }
}
