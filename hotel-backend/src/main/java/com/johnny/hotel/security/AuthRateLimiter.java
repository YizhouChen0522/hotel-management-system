package com.johnny.hotel.security;
import com.johnny.hotel.config.HotelRedisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.List;
import java.nio.charset.StandardCharsets;

@Component @RequiredArgsConstructor
public class AuthRateLimiter {
    private final StringRedisTemplate redis;
    private final HotelRedisProperties settings;
    // Redis 5 compatible. Saturated counters do not extend the original fixed window.
    private static final DefaultRedisScript<List> SCRIPT=new DefaultRedisScript<>("""
        local count = tonumber(redis.call('GET', KEYS[1]) or '0')
        local ttl = redis.call('PTTL', KEYS[1])
        if count == 0 or ttl < 0 then
            redis.call('SET', KEYS[1], 1, 'PX', ARGV[2])
            return {1, tonumber(ARGV[2])}
        end
        if count >= tonumber(ARGV[1]) then return {0, ttl} end
        redis.call('INCR', KEYS[1])
        return {1, ttl}
        """,List.class);
    public record Decision(boolean allowed,long retryAfterSeconds) {}
    public Decision acquire(String operation,String remoteAddress){
        int limit="login".equals(operation)?settings.getLoginLimit():settings.getRegistrationLimit();
        String key=key(operation,remoteAddress);
        var result=redis.execute(SCRIPT,List.of(key),Integer.toString(limit),Long.toString(settings.getAuthWindow().toMillis()));
        if(result==null||result.size()!=2)throw new IllegalStateException("Rate limiter result unavailable");
        return new Decision(((Number)result.get(0)).longValue()==1,Math.max(1,(((Number)result.get(1)).longValue()+999)/1000));
    }
    public String key(String operation,String address){
        // No account identifier is used, avoiding attacker-triggered account lockouts/enumeration.
        // A digest keeps raw peer addresses out of Redis keys (not a substitute for access controls).
        try {
            byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(address.getBytes(StandardCharsets.UTF_8));
            return settings.getPrefix()+"auth-rate:"+operation+":"+java.util.HexFormat.of().formatHex(bytes);
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
