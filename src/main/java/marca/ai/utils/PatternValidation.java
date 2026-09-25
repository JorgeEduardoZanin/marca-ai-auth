package marca.ai.utils;

import java.util.regex.Pattern;

public final class PatternValidation {

    private PatternValidation() {}

    // Maiuscula e aceita nas duas partes. RFC 5321 secao 2.4: a parte local
    // "MUST BE treated as case sensitive" e o dominio "not case sensitive" —
    // entao Jorge@Gmail.COM e um endereco valido, nao um erro de digitacao.
    // A unicidade case-insensitive fica com o indice lower(email) do banco.
    public static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+(\\.[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+)*" +
                    "@[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+$"
    );

    public static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[\\x21-\\x2F\\x3A-\\x40\\x5B-\\x60\\x7B-\\x7E])[\\x21-\\x7E]+$"
    );

    // 11 digitos. Era [1-9]{11}, que rejeitava qualquer CPF contendo zero.
    public static final Pattern CPF_PATTERN = Pattern.compile("^\\d{11}$");

    public static final Pattern NAME_PATTERN = Pattern.compile("^\\p{L}+([ '\\-]\\p{L}+)*$");

    // E.164 do Brasil, ja normalizado: +55 + DDD + celular (9 digitos) ou fixo (8 digitos).
    public static final Pattern TELEPHONE_PATTERN = Pattern.compile("^\\+55[1-9]{2}(9\\d{8}|[2-5]\\d{7})$");
}
