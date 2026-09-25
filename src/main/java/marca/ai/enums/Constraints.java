package marca.ai.enums;

public enum Constraints {

    USER_CPF_KEY("usuario_cpf_key"),
    EMAIL_KEY("uq_credencial_email"),
    UNKNOWN("");

    private final String constraintName;

    Constraints(String constraintName) {
        this.constraintName = constraintName;
    }

    public String getConstraintName() {
        return constraintName;
    }

    public static Constraints from(String constraintName) {
        if (constraintName == null) return UNKNOWN;

        for (Constraints constraint : values()) {
            if (constraint.constraintName.equals(constraintName)) return constraint;
        }
        return UNKNOWN;
    }
}
