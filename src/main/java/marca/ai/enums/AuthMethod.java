package marca.ai.enums;

public enum AuthMethod {

    OTP("otp"),
    PWD("pwd");

    private final String method;

    AuthMethod(String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }
}
