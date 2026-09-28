package marca.ai.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import marca.ai.exception.ValidationException;
import marca.ai.exception.type.ValidationExceptionType;
import marca.ai.utils.CpfValidation;
import marca.ai.utils.PatternValidation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public record CreateUserRequest (

        @JsonProperty("name")
        String name,

        @JsonProperty("cpf")
        String cpf,

        @JsonProperty("telephone")
        String telephone,

        @JsonProperty("date_of_birth")
        LocalDate dateOfBirth,

        @JsonProperty("email")
        String email,

        @JsonProperty("password")
        String password,

        @JsonProperty("terms_of_user")
        boolean termsOfUse,

        @JsonProperty("privacy_policy")
        boolean privacyPolicy,

        @JsonProperty("termsOfUseVersion")
        String termsOfUseVersion,

        @JsonProperty("privacy_policy_version")
        String privacyPolicyVersion
){

    public void validate () {

        List<ValidationExceptionType> errors = new ArrayList<>();

        if (isBlank(name)) errors.add(ValidationExceptionType.NAME_CANNOT_BE_NULL_OR_EMPTY);
        if (isBlank(cpf)) errors.add(ValidationExceptionType.CPF_CANNOT_BE_NULL_OR_EMPTY);
        if (isBlank(telephone)) errors.add(ValidationExceptionType.TELEPHONE_CANNOT_BE_NULL_OR_EMPTY);
        if (dateOfBirth == null) errors.add(ValidationExceptionType.DATE_OF_BIRTH_CANNOT_BE_NULL_OR_EMPTY);
        if (isBlank(email)) errors.add(ValidationExceptionType.EMAIL_CANNOT_BE_NULL_OR_EMPTY);
        if (isBlank(password)) errors.add(ValidationExceptionType.PASSWORD_CANNOT_BE_NULL_OR_EMPTY);
        if (isBlank(privacyPolicyVersion) || isBlank(termsOfUseVersion)) errors.add(ValidationExceptionType.TERMS_OF_USE_OR_PRIVACY_POLICY_IS_NULL);

        if (!privacyPolicy || !termsOfUse) errors.add(ValidationExceptionType.TERMS_AND_PRIVACY_POLICY_MUST_BE_ACCEPTED);

        if (!errors.isEmpty()) throw new ValidationException(errors);
    }

    public void validateContent () {

        List<ValidationExceptionType> errors = new ArrayList<>();

        if (!PatternValidation.NAME_PATTERN.matcher(name).matches()) errors.add(ValidationExceptionType.NAME_MUST_NOT_CONTAIN_SPECIAL_CHARACTERS);
        if (name.length() > 127) errors.add(ValidationExceptionType.NAME_MUST_NOT_EXCEED_127_CHARACTERS);

        if (!PatternValidation.CPF_PATTERN.matcher(cpf).matches()) errors.add(ValidationExceptionType.CPF_MUST_CONTAIN_ONLY_11_DIGITS);
        else if (!CpfValidation.isValid(cpf)) errors.add(ValidationExceptionType.CPF_INVALID);

        if (!PatternValidation.TELEPHONE_PATTERN.matcher(telephone).matches()) errors.add(ValidationExceptionType.TELEPHONE_INVALID_FORMAT);

        if (!PatternValidation.EMAIL_PATTERN.matcher(email).matches()) errors.add(ValidationExceptionType.EMAIL_INVALID_FORMAT);
        if (email.length() > 254) errors.add(ValidationExceptionType.EMAIL_MUST_NOT_EXCEED_254_CHARACTERS);

        if (!PatternValidation.PASSWORD_PATTERN.matcher(password).matches()) errors.add(ValidationExceptionType.PASSWORD_INVALID_FORMAT);
        if (password.length() < 8 || password.length() > 64) errors.add(ValidationExceptionType.PASSWORD_MUST_HAVE_BETWEEN_8_AND_64_CHARACTERS);

        if (dateOfBirth.isAfter(LocalDate.now())) errors.add(ValidationExceptionType.DATE_OF_BIRTH_CANNOT_BE_IN_THE_FUTURE);

        if (!errors.isEmpty()) throw new ValidationException(errors);
    }

    private static boolean isBlank (String value) {
        return value == null || value.isBlank();
    }
}
