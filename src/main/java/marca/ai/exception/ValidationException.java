package marca.ai.exception;

import marca.ai.exception.type.ValidationExceptionType;

import java.util.List;
import java.util.stream.Collectors;

public class ValidationException extends RuntimeException {

    private final List<ValidationExceptionType> errors;

    public ValidationException(ValidationExceptionType error) {
        this(List.of(error));
    }

    public ValidationException(List<ValidationExceptionType> errors) {
        this.errors = List.copyOf(errors);
    }

    public List<ValidationExceptionType> getErrors() {
        return errors;
    }
}
