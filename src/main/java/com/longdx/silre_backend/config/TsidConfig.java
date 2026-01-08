package com.longdx.silre_backend.config;

import com.github.f4b6a3.tsid.TsidFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration
public class TsidConfig {

    private static final Logger logger = LoggerFactory.getLogger(TsidConfig.class);
    
    @Autowired(required = false) // Make Redis optional
    private StringRedisTemplate redisTemplate;

    private static final String NODE_KEY_PREFIX = "sys:tsid:node:";
    private static final int MAX_NODE_ID = 1024; // 10 bits
    private static final int DEFAULT_NODE_ID = 0; // Fallback node ID when Redis is unavailable

    @Bean
    public TsidFactory tsidFactory() {
        int nodeId = allocateNodeId();
        // Log quan trọng để debug xem instance đang chạy node nào
        logger.info(">>> TSID Initialized with Node ID: {}", nodeId);
        
        TsidFactory factory = TsidFactory.builder()
                .withNode(nodeId)
                .build();
        
        // Inject into custom generator for Hibernate 7.2 compatibility
        TsidIdGenerator.setTsidFactory(factory);
        
        return factory;
    }

    private int allocateNodeId() {
        // If Redis is not available, use default node ID
        if (redisTemplate == null) {
            logger.warn("Redis not available - using default Node ID: {}. TSID uniqueness may be compromised in multi-instance deployments.", DEFAULT_NODE_ID);
            return DEFAULT_NODE_ID;
        }

        try {
            // Try to allocate node ID from Redis
            for (int i = 0; i < MAX_NODE_ID; i++) {
                String key = NODE_KEY_PREFIX + i;
                // Lock slot này trong 24h. 
                // Nếu app crash, slot sẽ tự nhả sau 24h (hoặc khi restart Redis)
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent(key, "LOCKED", Duration.ofHours(24));

                if (Boolean.TRUE.equals(acquired)) {
                    keepAlive(key); // Chạy thread ngầm để gia hạn TTL
                    logger.info("Successfully allocated TSID Node ID: {} from Redis", i);
                    return i;
                }
            }
            logger.warn("All TSID Node IDs (0-{}) are in use. Falling back to default Node ID: {}", MAX_NODE_ID - 1, DEFAULT_NODE_ID);
            return DEFAULT_NODE_ID;
        } catch (RedisConnectionFailureException e) {
            // Redis connection failed - use fallback
            logger.warn("Unable to connect to Redis for TSID node allocation: {}. Using default Node ID: {}. TSID uniqueness may be compromised in multi-instance deployments.", 
                    e.getMessage(), DEFAULT_NODE_ID);
            return DEFAULT_NODE_ID;
        } catch (Exception e) {
            // Any other Redis error - use fallback
            logger.warn("Error allocating TSID node ID from Redis: {}. Using default Node ID: {}. TSID uniqueness may be compromised in multi-instance deployments.", 
                    e.getMessage(), DEFAULT_NODE_ID);
            return DEFAULT_NODE_ID;
        }
    }
    
    /**
     * Logic gia hạn heartbeat để giữ lock trong Redis
     * Chạy background thread để refresh TTL mỗi 12 giờ
     */
    private void keepAlive(String key) {
        if (redisTemplate == null) {
            return; // Skip keep-alive if Redis is not available
        }
        
        Thread keepAliveThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(Duration.ofHours(12).toMillis());
                    if (redisTemplate != null) {
                        redisTemplate.expire(key, Duration.ofHours(24));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.warn("Error refreshing TSID node lock in Redis: {}", e.getMessage());
                    break;
                }
            }
        });
        keepAliveThread.setDaemon(true);
        keepAliveThread.setName("TSID-KeepAlive-" + key);
        keepAliveThread.start();
    }
}

