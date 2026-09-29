package marca.ai.repository;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.TransactionPropagation;
import jakarta.enterprise.context.ApplicationScoped;
import marca.ai.dto.request.CreateUserRequest;

import java.util.UUID;

@ApplicationScoped
public class UserRepository {

    private final Pool pool;

    public UserRepository(Pool pool) {
        this.pool = pool;
    }

    public Uni<UUID> insertUser(CreateUserRequest request) {

        String sql = """
                INSERT INTO marca_ai_auth.usuario (
                   nome,
                   cpf,
                   telefone,
                   data_nascimento
                ) VALUES ($1, $2, $3, $4)
                RETURNING id
                """;

        Tuple params = Tuple.tuple()
                .addString(request.name())
                .addString(request.cpf())
                .addString(request.telephone())
                .addLocalDate(request.dateOfBirth());

        return pool.withTransaction(TransactionPropagation.CONTEXT, connection ->
                connection.preparedQuery(sql)
                        .execute(params)
                        .map(rows -> rows.iterator().next().getUUID("id")));
    }
}
