package marca.ai.repository;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.TransactionPropagation;
import jakarta.enterprise.context.ApplicationScoped;
import marca.ai.model.RoleModel;

import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class RoleRepository {

    private final Pool pool;

    public RoleRepository(Pool pool) {
        this.pool = pool;
    }

    public Uni<RoleModel> findRole (UUID ID) {

        String sql = """
                SELECT
                    c.usuario_id,
                    c.email,
                    ARRAY(SELECT p.papel FROM marca_ai_auth.papel_usuario p
                            WHERE p.usuario_id = c.usuario_id) AS papeis,
                    ARRAY(SELECT me.empresa_id FROM marca_ai_auth.membro_empresa me
                            WHERE me.usuario_id = c.usuario_id AND me.papel = 'DONO') AS dono,
                    ARRAY(SELECT me.empresa_id FROM marca_ai_auth.membro_empresa me
                            WHERE me.usuario_id = c.usuario_id AND me.papel = 'FUNCIONARIO') AS funcionario
                FROM marca_ai_auth.credencial c
                WHERE c.usuario_id = $1
                """;

        return pool.withTransaction(TransactionPropagation.CONTEXT, connection ->
                connection.preparedQuery(sql)
                .execute(Tuple.of(ID))
                .map(rowSet -> {
                    if (rowSet.size() == 0) return null;

                    Row row = rowSet.iterator().next();

                    return new RoleModel(
                            row.getUUID("usuario_id"),
                            row.getString("email"),
                            Set.of(row.getArrayOfStrings("papeis")),
                            Set.of(row.getArrayOfUUIDs("dono")),
                            Set.of(row.getArrayOfUUIDs("funcionario"))
                    );
                }));
    }
}
