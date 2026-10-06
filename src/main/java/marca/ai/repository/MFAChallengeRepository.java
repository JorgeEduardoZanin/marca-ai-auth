package marca.ai.repository;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import marca.ai.model.MFAModel;

import java.time.OffsetDateTime;
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

    public Uni<Void> updateMFAChallenge(UUID userID, OffsetDateTime completedOn, int attempts) {

        String sql = """
                UPDATE marca_ai_auth.desafio_mfa
                SET concluido_em = $1,
                    tentativas   = $2
                WHERE id = (SELECT id
                            FROM marca_ai_auth.desafio_mfa
                            WHERE usuario_id = $3 AND concluido_em IS NULL
                            ORDER BY criado_em DESC
                            LIMIT 1)
                """;

        Tuple params = Tuple.tuple()
                .addOffsetDateTime(completedOn)
                .addInteger(attempts)
                .addUUID(userID);

        return pool.preparedQuery(sql)
                .execute(params)
                .replaceWithVoid();
    }

    public Uni<MFAModel> findMFAChallenge(UUID ID){

        String sql = """
                SELECT 
                    dm.finalidade,
                    dm.token_hash,
                    dm.tentativas,
                    dm.expira_em
                FROM marca_ai_auth.desafio_mfa dm
                WHERE dm.usuario_id = $1
                  AND dm.concluido_em IS NULL
                  AND dm.expira_em > now()
                ORDER BY dm.criado_em DESC
                LIMIT 1
                """;

        return pool.preparedQuery(sql)
                .execute(Tuple.of(ID))
                .map(rowSet -> {
                    if (rowSet.size() == 0) return null;

                    Row row = rowSet.iterator().next();

                    return new MFAModel(
                            row.getString("finalidade"),
                            row.getString("token_hash"),
                            row.getInteger("tentativas"),
                            row.getOffsetDateTime("expira_em")
                    );
                });

    }
}
