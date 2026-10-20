package marca.ai.repository;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.TransactionPropagation;
import jakarta.enterprise.context.ApplicationScoped;
import marca.ai.dto.input.RefreshTokenInput;
import marca.ai.model.MFAModel;
import marca.ai.model.RefreshTokenModel;

import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class RefreshTokenRepository {


    private final Pool pool;

    public RefreshTokenRepository(Pool pool) {
        this.pool = pool;
    }

    public Uni<Void> insertRefreshToken (RefreshTokenInput input) {

        String sql = """
                INSERT INTO marca_ai_auth.refresh_token (
                    usuario_id,
                    familia_id,
                    token_hash,
                    metodos_autenticacao,
                    autenticado_em,
                    expira_em,
                    ip_criacao,
                    user_agent,
                    criado_em
                    ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
                """;

        Tuple params = Tuple.tuple()
                .addUUID(input.userID())
                .addUUID(input.familyID())
                .addString(input.tokenHash())
                .addArrayOfString(input.authMethod())
                .addOffsetDateTime(input.authAt())
                .addOffsetDateTime(input.expireIn())
                .addString(input.ipCreation())
                .addString(input.userAgent())
                .addOffsetDateTime(input.createdAt());

        return pool.withTransaction(TransactionPropagation.CONTEXT, connection ->
                connection.preparedQuery(sql)
                        .execute(params)
                        .replaceWithVoid());
    }

    public Uni<Void> updatePreviousToken (String token) {

        String sql = """
                UPDATE marca_ai_auth.refresh_token
                SET usado_em = now()
                WHERE token_hash = $1
                AND usado_em IS NULL
                """;

        return pool.withTransaction(TransactionPropagation.CONTEXT, connection ->
                connection.preparedQuery(sql)
                        .execute(Tuple.of(token))
                        .replaceWithVoid());
    }

    public Uni<RefreshTokenModel> findRefreshToken (String tokenHash) {

        String sql = """
                SELECT 
                    rt.id,
                    rt.usuario_id,
                    rt.familia_id,
                    rt.autenticado_em,
                    rt.expira_em,
                    rt.usado_em,
                    rt.revogado_em
                FROM marca_ai_auth.refresh_token rt
                WHERE rt.token_hash = $1
                """;

        return pool.preparedQuery(sql)
                .execute(Tuple.of(tokenHash))
                .map(rowSet -> {
                    if (rowSet.size() == 0) return null;

                    Row row = rowSet.iterator().next();

                    return new RefreshTokenModel(
                            row.getUUID("id"),
                            row.getUUID("usuario_id"),
                            row.getUUID("familia_id"),
                            row.getOffsetDateTime("autenticado_em"),
                            row.getOffsetDateTime("expira_em"),
                            row.getOffsetDateTime("usado_em"),
                            row.getOffsetDateTime("revogado_em")
                    );
                });
    }

    public Uni<Void> revokesRefreshTokens (UUID familyId) {

        String sql = """
                UPDATE marca_ai_auth.refresh_token
                SET revogado_em = now()
                WHERE familia_id = $1
                """;

        return pool.preparedQuery(sql)
                .execute(Tuple.of(familyId))
                .replaceWithVoid();


    }
}
