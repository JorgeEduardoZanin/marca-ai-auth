package marca.ai.repository;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.TransactionPropagation;
import jakarta.enterprise.context.ApplicationScoped;
import marca.ai.model.AuthFactorModel;
import marca.ai.model.MFAModel;

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

    public Uni<AuthFactorModel> findAuthFactor (UUID ID) {

        String sql = """
                SELECT 
                    fa.segredo_cifrado,
                    fa.verificado_em,
                    c.email
                FROM marca_ai_auth.fator_autenticacao fa
                INNER JOIN marca_ai_auth.credencial c ON c.usuario_id = fa.usuario_id
                WHERE fa.usuario_id = $1
                """;

        return pool.preparedQuery(sql)
                .execute(Tuple.of(ID))
                .map(rowSet -> {

                    if (rowSet.size() == 0) return null;

                    Row row = rowSet.iterator().next();

                    return new AuthFactorModel(
                            row.getString("segredo_cifrado"),
                            row.getOffsetDateTime("verificado_em") != null,
                            row.getString("email")
                    );
                });
    }

    public Uni<Boolean> registerUse (UUID ID, long step) {

        String sql = """
                UPDATE marca_ai_auth.fator_autenticacao
                SET ultimo_passo_totp = $2,
                    ultimo_uso_em     = now(),
                    verificado_em     = COALESCE(verificado_em, now())
                WHERE usuario_id = $1
                  AND (ultimo_passo_totp IS NULL OR ultimo_passo_totp < $2)
                """;

        Tuple params = Tuple.tuple()
                .addUUID(ID)
                .addLong(step);

        return pool.preparedQuery(sql)
                .execute(params)
                .map(rows -> rows.rowCount() == 1);
    }
}
