package marca.ai.exception.mapper;

import io.quarkus.logging.Log;
import io.vertx.pgclient.PgException;
import jakarta.ws.rs.core.Response;
import marca.ai.enums.Constraints;
import marca.ai.enums.ViolationCodes;
import marca.ai.exception.BusinessRuleException;
import marca.ai.exception.InfrastructureException;
import marca.ai.exception.type.BusinessRuleExceptionType;
import marca.ai.exception.type.InfrastructureExceptionType;

/**
 * Traduz erro do Postgres em excecao de dominio, para o handler correspondente
 * devolver o status certo.
 *
 * A classificacao e por SQLSTATE, nao pela mensagem: o codigo e padronizado e
 * estavel entre versoes, enquanto o texto muda com versao e locale do servidor.
 */
public final class ExceptionMapper {

    private ExceptionMapper() {}

    public static RuntimeException fromPgException (PgException failure) {

        if (ViolationCodes.UNIQUE.getCode().equals(failure.getSqlState())) {
            return switch (Constraints.from(failure.getConstraint())) {
                case USER_CPF_KEY -> new BusinessRuleException(BusinessRuleExceptionType.CPF_ALREADY_REGISTERED);
                case EMAIL_KEY    -> new BusinessRuleException(BusinessRuleExceptionType.EMAIL_ALREADY_REGISTERED);
                case UNKNOWN      -> unknownInfrastructureError();
            };
        }
        Log.errorf("Erro desconhecido. erro=%s", failure.getMessage());
        return unknownInfrastructureError();
    }

    private static InfrastructureException unknownInfrastructureError () {
        return new InfrastructureException(
                InfrastructureExceptionType.UNKNOWN_INFRASTRUCTURE_ERROR,
                Response.Status.INTERNAL_SERVER_ERROR);
    }
}
