package marca.ai.enums;

public enum ViolationCodes {

    UNIQUE("23505"),
    NOT_NULL("23502"),
    FK_VIOLATION("23503"),
    MALFORMED_DATE("22007");

    private final String code;

    ViolationCodes(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
