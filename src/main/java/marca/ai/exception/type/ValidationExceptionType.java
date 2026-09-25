package marca.ai.exception.type;

import jakarta.ws.rs.core.Response;

public enum ValidationExceptionType {

    NAME_CANNOT_BE_NULL_OR_EMPTY("Nome não pode ser nulo ou vazio."),
    CPF_CANNOT_BE_NULL_OR_EMPTY("Cpf não pode ser nulo ou vazio."),
    CNPJ_CANNOT_BE_NULL_OR_EMPTY("Cnpj não pode ser nulo ou vazio."),
    DATE_OF_BIRTH_CANNOT_BE_NULL_OR_EMPTY("Data de nascimento não pode ser nula ou vazia."),
    TELEPHONE_CANNOT_BE_NULL_OR_EMPTY("Telefone não pode ser nulo ou vazio."),
    EMAIL_CANNOT_BE_NULL_OR_EMPTY("Email não pode ser nulo ou vazio."),
    PASSWORD_CANNOT_BE_NULL_OR_EMPTY("Senha não pode ser nula ou vazia."),

    CPF_MUST_CONTAIN_ONLY_11_DIGITS("CPF deve conter exatamente 11 dígitos numéricos."),
    EMAIL_INVALID_FORMAT("E-mail inválido."),
    EMAIL_MUST_NOT_EXCEED_254_CHARACTERS("E-mail não pode ter mais de 254 caracteres."),
    TELEPHONE_INVALID_FORMAT("Telefone deve conter DDD e número válido."),
    NAME_MUST_NOT_EXCEED_127_CHARACTERS("Nome não pode ter mais de 127 caracteres."),
    NAME_MUST_NOT_CONTAIN_SPECIAL_CHARACTERS("Nome não pode conter números ou caracteres especiais, exceto hífen e apóstrofo."),
    PASSWORD_MUST_HAVE_BETWEEN_8_AND_64_CHARACTERS("Senha deve ter entre 8 e 64 caracteres."),
    PASSWORD_INVALID_FORMAT("Senha deve conter letra maiúscula, letra minúscula, número e caractere especial, sem espaços ou acentos."),
    CPF_INVALID("CPF inválido."),
    DATE_OF_BIRTH_CANNOT_BE_IN_THE_FUTURE("Data de nascimento não pode estar no futuro."),
    TERMS_AND_PRIVACY_POLICY_MUST_BE_ACCEPTED("Não é possível se cadastrar sem aceitar os termos de uso e a política de privacidade.");



    private final String message;

    ValidationExceptionType(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
