package marca.ai.utils;

import java.util.regex.Pattern;

public final class PatternValidation {

    private PatternValidation() {}

    public static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+(\\.[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+)*" +
                    "@[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+$"
    );

    public static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[\\x21-\\x2F\\x3A-\\x40\\x5B-\\x60\\x7B-\\x7E])[\\x21-\\x7E]+$"
    );

    public static final Pattern CPF_PATTERN = Pattern.compile("^\\d{11}$");

    public static final Pattern NAME_PATTERN = Pattern.compile("^\\p{L}+([ '\\-]\\p{L}+)*$");

    public static final Pattern TELEPHONE_PATTERN = Pattern.compile("^\\+55[1-9]{2}(9\\d{8}|[2-5]\\d{7})$");
}
