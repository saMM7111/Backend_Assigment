package com.sam.assigment.service;

import com.sam.assigment.api.exception.TooManyRequestsException;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class BotReplyGuardrailService {

    private static final int MAX_BOT_REPLIES_PER_POST = 100;
    private static final int MAX_DEPTH_LEVEL = 20;
    private static final Duration COOLDOWN_WINDOW = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public BotReplyGuardrailService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void enforceDepthCap(int depthLevel) {
        if (depthLevel > MAX_DEPTH_LEVEL) {
            throw new TooManyRequestsException(
                    "Vertical cap reached. depthLevel must be <= " + MAX_DEPTH_LEVEL
            );
        }
    }

    public Reservation reserveBotReply(Long postId, Long botId, Long humanId) {
        String botCountKey = botCountKey(postId);
        Long updatedCount = redisTemplate.opsForValue().increment(botCountKey);

        if (updatedCount == null) {
            throw new IllegalStateException("Could not increment bot counter for post " + postId);
        }

        if (updatedCount > MAX_BOT_REPLIES_PER_POST) {
            redisTemplate.opsForValue().decrement(botCountKey);
            throw new TooManyRequestsException(
                    "Horizontal cap reached. A post can have at most " + MAX_BOT_REPLIES_PER_POST + " bot replies."
            );
        }

        String cooldownKey = null;
        if (humanId != null) {
            cooldownKey = cooldownKey(botId, humanId);
            Boolean allowed = redisTemplate.opsForValue().setIfAbsent(cooldownKey, "1", COOLDOWN_WINDOW);
            if (!Boolean.TRUE.equals(allowed)) {
                redisTemplate.opsForValue().decrement(botCountKey);
                throw new TooManyRequestsException("Cooldown cap active for this bot-human interaction.");
            }
        }

        return new Reservation(botCountKey, cooldownKey);
    }

    public void rollbackReservation(Reservation reservation) {
        if (reservation == null) {
            return;
        }

        redisTemplate.opsForValue().decrement(reservation.botCountKey());
        if (reservation.cooldownKey() != null) {
            redisTemplate.delete(reservation.cooldownKey());
        }
    }

    private String botCountKey(Long postId) {
        return "post:" + postId + ":bot_count";
    }

    private String cooldownKey(Long botId, Long humanId) {
        return "cooldown:bot_" + botId + ":human_" + humanId;
    }

    public record Reservation(String botCountKey, String cooldownKey) {
    }
}
