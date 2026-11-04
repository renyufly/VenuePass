package com.venuepass.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.venuepass.common.BusinessException;
import com.venuepass.model.Models.ReservationInput;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class HotReservationService {
    public static final String TOPIC = "venuepass-hot-reservations";
    private static final DefaultRedisScript<Long> HOLD = new DefaultRedisScript<>(
        "if redis.call('exists',KEYS[1])==0 then return -3 end " +
        "if redis.call('exists',KEYS[2])==1 then return -2 end " +
        "if tonumber(redis.call('get',KEYS[1]))<1 then return -1 end " +
        "redis.call('decr',KEYS[1]); redis.call('set',KEYS[2],'1'); return 1", Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
        "if redis.call('del',KEYS[2])==1 and redis.call('exists',KEYS[1])==1 then return redis.call('incr',KEYS[1]) end return 0", Long.class);

    public record Request(long sessionId, String username, String participantName) { }
    private final StringRedisTemplate redis;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;

    public HotReservationService(StringRedisTemplate redis, KafkaTemplate<String, String> kafka, ObjectMapper json) {
        this.redis = redis; this.kafka = kafka; this.json = json;
    }

    private String stock(long id) { return "session:" + id + ":stock"; }
    private String claim(long id, String username) { return "session:" + id + ":user:" + username; }
    private String outcome(long id, String username) { return "session:" + id + ":result:" + username; }

    public void preheat(long sessionId, int available) {
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(stock(sessionId), Integer.toString(available))))
            throw new BusinessException("Session inventory already preheated");
    }

    public String enqueue(ReservationInput input, String username) {
        long id = input.sessionId();
        Long held = redis.execute(HOLD, List.of(stock(id), claim(id, username)));
        if (held == null || held != 1L) {
            if (held != null && held == -3L) throw new BusinessException("Hot inventory is not preheated");
            if (held != null && held == -2L) throw new BusinessException("Already reserved or queued");
            throw new BusinessException("Session sold out");
        }
        try {
            redis.opsForValue().set(outcome(id, username), "QUEUED");
            kafka.send(TOPIC, Long.toString(id), json.writeValueAsString(new Request(id, username, input.participantName()))).get(5, TimeUnit.SECONDS);
            return "QUEUED";
        } catch (TimeoutException exception) {
            // The broker may have accepted the message. Keep the hold until the queue outcome is known.
            return "QUEUED";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "QUEUED";
        } catch (Exception exception) {
            release(id, username);
            redis.opsForValue().set(outcome(id, username), "FAILED:queue unavailable");
            throw new BusinessException("Reservation queue unavailable");
        }
    }

    public Request decode(String payload) {
        try { return json.readValue(payload, Request.class); }
        catch (JacksonException exception) { throw new BusinessException("Invalid reservation message"); }
    }

    public void result(long id, String username, String value) {
        redis.opsForValue().set(outcome(id, username), value);
    }

    public String result(long id, String username) { return redis.opsForValue().get(outcome(id, username)); }

    public void release(long id, String username) {
        redis.execute(RELEASE, List.of(stock(id), claim(id, username)));
    }

    public void clear(long id) { redis.delete(stock(id)); }
}
