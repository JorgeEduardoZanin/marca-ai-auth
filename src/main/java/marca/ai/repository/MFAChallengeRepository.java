package marca.ai.repository;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.TransactionPropagation;
import jakarta.enterprise.context.ApplicationScoped;
import marca.ai.model.MFAModel;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class MFAChallengeRepository {

    private final Pool pool;

    public MFAChallengeRepository(Pool pool) {
        this.pool = pool;
    }

    public Uni<Void> insertMFAChallenge(UUID userID, String purpose, UUID familyID, String tokenHash, OffsetDateTime expiresIn) {

        String sql = """
                INSERT INTO marca_ai_auth.desafio_mfa (
                    usuario_id,
                    finalidade,
                    familia_id,
                    token_hash,
                    expira_em
                ) VALUES ($1, $2, $3, $4, $5)
                """;

        Tuple params = Tuple.tuple()
                .addUUID(userID)
                .addString(purpose)
                .addUUID(familyID)
                .addString(tokenHash)
                .addOffsetDateTime(expiresIn);

        return pool.preparedQuery(sql)
                .execute(params)
                .replaceWithVoid();
    }

    public Uni<Void> updateMFAChallenge(String tokenHash, OffsetDateTime completedOn, int attempts) {

        String sql = """
                UPDATE marca_ai_auth.desafio_mfa
                SET concluido_em = $1,
                    tentativas   = $2
                WHERE token_hash = $3
                """;

        Tuple params = Tuple.tuple()
                .addOffsetDateTime(completedOn)
                .addInteger(attempts)
                .addString(tokenHash);

        return pool.withTransaction(TransactionPropagation.CONTEXT, connection ->
                connection.preparedQuery(sql)
                        .execute(params)
                        .replaceWithVoid());
    }

    public Uni<MFAModel> findMFAChallenge(String tokenHash){

        String sql = """
                SELECT
                    dm.tentativas,
                    c.usuario_id,
                    c.email,
                    fa.segredo_cifrado,
                    ARRAY(SELECT p.papel FROM marca_ai_auth.papel_usuario p
                            WHERE p.usuario_id = c.usuario_id) AS papeis,
                    ARRAY(SELECT me.empresa_id FROM marca_ai_auth.membro_empresa me
                            WHERE me.usuario_id = c.usuario_id AND me.papel = 'DONO') AS dono,
                    ARRAY(SELECT me.empresa_id FROM marca_ai_auth.membro_empresa me
                            WHERE me.usuario_id = c.usuario_id AND me.papel = 'FUNCIONARIO') AS funcionario
                FROM marca_ai_auth.desafio_mfa dm
                INNER JOIN marca_ai_auth.credencial c ON c.usuario_id = dm.usuario_id
                INNER JOIN marca_ai_auth.fator_autenticacao fa ON fa.usuario_id = c.usuario_id
                WHERE dm.token_hash = $1
                AND dm.concluido_em IS NULL
                AND dm.expira_em > now()
                """;

        return pool.preparedQuery(sql)
                .execute(Tuple.of(tokenHash))
                .map(rowSet -> {
                    if (rowSet.size() == 0) return null;

                    Row row = rowSet.iterator().next();

                    return new MFAModel(
                            row.getUUID("usuario_id"),
                            row.getString("email"),
                            row.getInteger("tentativas"),
                            row.getString("segredo_cifrado"),
                            Set.of(row.getArrayOfStrings("papeis")),
                            Set.of(row.getArrayOfUUIDs("dono")),
                            Set.of(row.getArrayOfUUIDs("funcionario"))
                    );
                });

    }
}
