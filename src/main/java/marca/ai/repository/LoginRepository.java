package marca.ai.repository;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import marca.ai.model.EnterpriseMemberModel;
import marca.ai.model.LoginModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class LoginRepository {

    private final Pool pool;

    public LoginRepository(Pool pool) {
        this.pool = pool;
    }

    public Uni<LoginModel> login (String email) {

        String sql = """
                SELECT 
                    c.usuario_id,
                    c.senha_hash,
                    c.email_verificado_em,
                    c.tentativas_falhas,
                    c.bloqueado_ate,
                    c.senha_alterada_em,
                    EXISTS(SELECT 1 FROM marca_ai_auth.fator_autenticacao f 
                        WHERE f.usuario_id = c.usuario_id AND f.verificado_em IS NOT NULL) AS mfa_ativo
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
                            row.getLocalDateTime("email_verificado_em"),
                            row.getInteger("tentativas_falhas"),
                            row.getLocalDateTime("bloqueado_ate"),
                            row.getLocalDateTime("senha_alterada_em"),
                            row.getInteger("mfa_ativo") == 1,
                            Set.of(row.getArrayOfStrings("papeis")),
                            Set.of(row.getArrayOfUUIDs("dono")),
                            Set.of(row.getArrayOfUUIDs("funcionario"))
                    );

                });
    }

}
