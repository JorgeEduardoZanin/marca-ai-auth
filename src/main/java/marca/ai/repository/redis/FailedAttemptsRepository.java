package marca.ai.repository.redis;

import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;

@ApplicationScoped
public class FailedAttemptsRepository {

    private static final int DURATION_FAILED_ATTEMPTS_DAYS = 7;

    private static final int DURATION_FAILED_ATTEMPTS_MINUTE_IP = 1;

    private static final int DURATION_FAILED_ATTEMPTS_HOURS_FALSE_EMAIL = 6;

    private static final int DURATION_BLOCKS_IP = 24;

    private static final String KEY_FAILED_ATTEMPTS = "login:failures:";

    private static final String KEY_FAILED_ATTEMPTS_IP = "login:failures:ip:";

    private static final String KEY_FAILED_ATTEMPTS_FALSE_EMAIL = "login:failures:false:email:ip:";

    private static final String KEY_BLOCKS_IP = "blocks:ip:";

    private final ReactiveValueCommands<String, Long> counters;

    private final ReactiveKeyCommands<String> keys;

    public FailedAttemptsRepository(ReactiveRedisDataSource ds) {
        this.counters = ds.value(Long.class);
        this.keys = ds.key();
    }

    public Uni<Long> insertFailedAttempts(String email) {
        String key = KEY_FAILED_ATTEMPTS + email;
        return counters.incr(key)
                .call(total -> total == 1
                        ? keys.expire(key, Duration.ofDays(DURATION_FAILED_ATTEMPTS_DAYS))
                        : Uni.createFrom().voidItem());
    }

    public Uni<Integer> clearFaults(String email) {
        return keys.del(KEY_FAILED_ATTEMPTS + email);
    }

    public Uni<Long> insertFailedAttemptsIP(String ip) {
        String key = KEY_FAILED_ATTEMPTS_IP + ip;
        return counters.incr(key)
                .call(total -> total == 1
                        ? keys.expire(key, Duration.ofMinutes(DURATION_FAILED_ATTEMPTS_MINUTE_IP))
                        : Uni.createFrom().voidItem());
    }

    public Uni<Long> insertFailedAttemptsFalseEmail(String ip) {
        String key = KEY_FAILED_ATTEMPTS_FALSE_EMAIL + ip;
        return counters.incr(key)
                .call(total -> total == 1
                        ? keys.expire(key, Duration.ofHours(DURATION_FAILED_ATTEMPTS_HOURS_FALSE_EMAIL))
                        : Uni.createFrom().voidItem());
    }

    public Uni<Long> insertBlocksIP (String ip) {
        String key = KEY_BLOCKS_IP + ip;
        return counters.incr(key)
                .call(total -> total == 1
                        ? keys.expire(key, Duration.ofHours(DURATION_BLOCKS_IP))
                        : Uni.createFrom().voidItem());
    }

    public Uni<Long> getBlocksIp (String ip) {
        return counters.get(KEY_BLOCKS_IP + ip);
    }


}