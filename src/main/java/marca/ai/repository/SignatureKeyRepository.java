package marca.ai.repository;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.TransactionPropagation;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SignatureKeyRepository {

    private final Pool pool;

    public SignatureKeyRepository(Pool pool) {
        this.pool = pool;
    }

    public Uni<Void> insertKeys (String kid, String publicKey, String encryptedPrivateKey) {

        return pool.withTransaction(TransactionPropagation.CONTEXT, connection ->
                demoteActiveKey(connection)
                        .chain(() -> insertActiveKey(connection, kid, publicKey, encryptedPrivateKey)));
    }

    private Uni<Void> demoteActiveKey (SqlConnection connection) {

        final String sql = """
            UPDATE marca_ai_auth.chave_assinatura
               SET status = 'ANTERIOR'
             WHERE status = 'ATIVA'
            """;

        return connection.preparedQuery(sql)
                .execute()
                .replaceWithVoid();
    }

    private Uni<Void> insertActiveKey (SqlConnection connection, String kid, String publicKey, String encryptedPrivateKey) {

        final String INSERT_ACTIVE_KEY = """
            INSERT INTO marca_ai_auth.chave_assinatura (
                kid,
                chave_publica,
                chave_privada_cifrada,
                status
                ) VALUES ($1, $2, $3, 'ATIVA')
            """;

        Tuple params = Tuple.tuple()
                .addString(kid)
                .addString(publicKey)
                .addString(encryptedPrivateKey);

        return connection.preparedQuery(INSERT_ACTIVE_KEY)
                .execute(params)
                .replaceWithVoid();
    }

    public Uni<Boolean> existsActiveSignatureKey () {

         final String EXISTS_ACTIVE_KEY = """
            SELECT EXISTS (
                SELECT 1 FROM marca_ai_auth.chave_assinatura
                 WHERE status = 'ATIVA'
            ) AS existe
            """;

        return pool.preparedQuery(EXISTS_ACTIVE_KEY)
                .execute()
                .map(rowSet -> rowSet.iterator().next().getBoolean("existe"));
    }
}
