package marca.ai.utils;

/**
 * Dígito verificador do CPF. O schema delega esta checagem para a aplicação
 * (-- só dígitos, validados na aplicação), e o tipo char(11) do banco não
 * protege: ele aceita valor curto preenchendo com espaços.
 */
public final class CpfValidation {

    private CpfValidation() {}

    public static boolean isValid(String cpf) {
        if (cpf == null || !PatternValidation.CPF_PATTERN.matcher(cpf).matches()) return false;

        if (cpf.chars().distinct().count() == 1) return false;

        return digit(cpf, 9, 10) == value(cpf, 9)
            && digit(cpf, 10, 11) == value(cpf, 10);
    }

    private static int digit(String cpf, int length, int firstWeight) {
        int sum = 0;
        for (int i = 0; i < length; i++) sum += value(cpf, i) * (firstWeight - i);
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }

    private static int value(String cpf, int index) {
        return cpf.charAt(index) - '0';
    }
}
