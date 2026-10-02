package marca.ai.repository;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.TransactionPropagation;
import jakarta.enterprise.context.ApplicationScoped;
import marca.ai.dto.request.CreateUserRequest;
import marca.ai.model.LoginModel;

import java.time.OffsetDateTime;
import java.util.Set;
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

    public Uni<LoginModel> login (String email) {

        String sql = """
                SELECT 
                    c.usuario_id,
                    c.senha_hash,
                    c.email_verificado_em,
                    c.bloqueado_ate,
                    c.senha_alterada_em,
                    EXISTS(SELECT 1 FROM marca_ai_auth.fator_autenticacao f 
                        WHERE f.usuario_id = c.usuario_id AND f.verificado_em IS NOT NULL) AS mfa_ativo,
                    ARRAY(SELECT p.papel
                        FROM marca_ai_auth.papel_usuario p
                        WHERE p.usuario_id = c.usuario_id) AS papeis,
                    ARRAY(SELECT me.empresa_id FROM marca_ai_auth.membro_empresa me                 
                        WHERE usuario_id = c.usuario_id AND me.papel = 'DONO') AS dono,           
                    ARRAY(SELECT me.empresa_id FROM marca_ai_auth.membro_empresa me                  
                        WHERE me.usuario_id = c.usuario_id AND me.papel = 'FUNCIONARIO') AS funcionario
                FROM marca_ai_auth.credencial c
                WHERE c.email = $1
                """;

        return pool.preparedQuery(sql)
                .execute(Tuple.of(email))
                .map(rowSet -> {
                    if (rowSet.size() == 0) return null;

                    Row row = rowSet.iterator().next();

                    return new LoginModel(
                            row.getUUID("usuario_id"),
                            row.getString("senha_hash"),
                            row.getOffsetDateTime("email_verificado_em"),
                            row.getOffsetDateTime("bloqueado_ate"),
                            row.getOffsetDateTime("senha_alterada_em"),
                            row.getBoolean("mfa_ativo"),
                            Set.of(row.getArrayOfStrings("papeis")),
                            Set.of(row.getArrayOfUUIDs("dono")),
                            Set.of(row.getArrayOfUUIDs("funcionario"))
                    );

                });
    }

    public Uni<Void> blocksUser (UUID ID, OffsetDateTime blockedUntil) {

        String sql = """
                UPDATE marca_ai_auth.credencial
                SET bloqueado_ate = $1
                WHERE usuario_id = $2
                """;

        Tuple params = Tuple.tuple()
                .addOffsetDateTime(blockedUntil)
                .addUUID(ID);

        return pool.preparedQuery(sql)
                .execute(params)
                .replaceWithVoid();
    }


    public Uni<Void> clearBlocksUser (UUID ID) {

        String sql = """
                UPDATE marca_ai_auth.credencial
                SET bloqueado_ate = NULL
                WHERE usuario_id = $1
                """;

        return pool.preparedQuery(sql)
                .execute(Tuple.of(ID))
                .replaceWithVoid();
    }


}
