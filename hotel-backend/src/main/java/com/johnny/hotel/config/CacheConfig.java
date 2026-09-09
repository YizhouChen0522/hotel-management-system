package com.johnny.hotel.config;

import com.johnny.hotel.vo.RoomTypeVO;
import com.johnny.hotel.vo.RoomRateVO;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Configuration
@EnableCaching
@EnableConfigurationProperties(HotelRedisProperties.class)
public class CacheConfig {

    public static final String ROOM_TYPE_DETAIL = "roomTypeDetail";
    public static final String ROOM_TYPE_LIST = "roomTypeList";
    public static final String ROOM_RATE_RANGE = "roomRateRange";

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory, HotelRedisProperties settings) {

        ObjectMapper mapper = JsonMapper.shared();

        JavaType listType = mapper.getTypeFactory()
                .constructCollectionType(List.class, RoomTypeVO.class);

        RedisSerializer<RoomTypeVO> detailSerializer =
                new JacksonJsonRedisSerializer<>(
                        mapper,
                        RoomTypeVO.class
                );

        RedisSerializer<List<RoomTypeVO>> listSerializer =
                new JacksonJsonRedisSerializer<>(
                        mapper,
                        listType
                );

        RedisCacheConfiguration detailConfig =
                baseConfig(settings.getDetailTtl(), detailSerializer, settings.getPrefix());

        RedisCacheConfiguration listConfig =
                baseConfig(settings.getListTtl(), listSerializer, settings.getPrefix());

        RedisSerializer<List<RoomRateVO>> rateSerializer = new JacksonJsonRedisSerializer<>(mapper,
                mapper.getTypeFactory().constructCollectionType(List.class, RoomRateVO.class));
        // Redis 4 defaults to asynchronous writes. Wait for eviction completion after commit,
        // allowing failures to be handled locally instead of silently losing a future's error.
        var writer = RedisCacheWriter.create(connectionFactory,
                config -> config.batchStrategy(BatchStrategies.scan(256)).immediateWrites());
        return new ResilientRedisCacheManager(writer,detailConfig,Map.of(
                ROOM_TYPE_DETAIL,detailConfig, ROOM_TYPE_LIST,listConfig,
                ROOM_RATE_RANGE,baseConfig(settings.getRateTtl(),rateSerializer,settings.getPrefix())));
    }

    private <T> RedisCacheConfiguration baseConfig(
            Duration ttl,
            RedisSerializer<T> valueSerializer, String prefix) {

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .computePrefixWith(
                        cacheName -> prefix + cacheName + ":"
                )
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(valueSerializer)
                );
    }
}
