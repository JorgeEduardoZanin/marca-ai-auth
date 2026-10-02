package marca.ai.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import marca.ai.exception.LoginException;
import marca.ai.exception.type.LoginExceptionType;
import marca.ai.utils.PatternValidation;

public record LoginRequest (

        @JsonProperty("email")
        String email,

        @JsonProperty("password")
        String password

) {

    public void validate () {

        if (email == null || email.isBlank() || password == null || password.isBlank()) throw new LoginException(LoginExceptionType.EMAIL_AND_PASSWORD_CANNOT_BE_NULL_OR_EMPTY);

        if (!PatternValidation.EMAIL_PATTERN.matcher(email).matches()) throw new LoginException(LoginExceptionType.EMAIL_INVALID_FORMAT);
        if (email.length() > 254) throw new LoginException(LoginExceptionType.EMAIL_MUST_NOT_EXCEED_254_CHARACTERS);

    }
}
