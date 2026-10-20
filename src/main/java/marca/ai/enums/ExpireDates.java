package marca.ai.enums;

public enum ExpireDates {

    EXPIRE_IN_ONE_MONTH(1),
    EXPIRE_IN_SEVEN_DAYS(7);

    private final int expire;

    ExpireDates(int expire) {
        this.expire = expire;
    }

    public int getExpire() {
        return expire;
    }
}
