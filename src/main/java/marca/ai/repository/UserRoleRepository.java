package marca.ai.repository;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.TransactionPropagation;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class UserRoleRepository {

    private final Pool pool;

    public UserRoleRepository(Pool pool) {
        this.pool = pool;
    }

    public Uni<Void> insertUserRole (UUID userID) {

        String sql = """
                INSERT INTO marca_ai_auth.papel_usuario (
                    usuario_id,
                    papel
                    ) VALUES ($1, 'CLIENTE')
                """;

        return pool.withTransaction(TransactionPropagation.CONTEXT, connection ->
                connection.preparedQuery(sql)
                        .execute(Tuple.of(userID))
                        .replaceWithVoid());
    }


}
