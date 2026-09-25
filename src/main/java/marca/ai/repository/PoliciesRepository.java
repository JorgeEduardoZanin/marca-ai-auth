package marca.ai.repository;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.TransactionPropagation;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class PoliciesRepository {

    private final Pool pool;

    public PoliciesRepository(Pool pool) {
        this.pool = pool;
    }

    public Uni<Void> insertTermsAndPolicies(UUID id, String privacyPolicyVersion, String termsOfUseVersion, String ip) {

        String sql = """
                INSERT INTO marca_ai_auth.aceite_termo (
                    usuario_id,
                    tipo,
                    versao,
                    ip
                    ) VALUES ($1, 'TERMOS_USO', $3, $2),
                             ($1, 'POLITICA_PRIVACIDADE', $4, $2)
                """;

        Tuple params = Tuple.tuple()
                .addUUID(id)
                .addString(ip)
                .addString(termsOfUseVersion)
                .addString(privacyPolicyVersion);


        return pool.withTransaction(TransactionPropagation.CONTEXT, connection ->
                connection.preparedQuery(sql)
                .execute(params)
                .replaceWithVoid());

    }
}
