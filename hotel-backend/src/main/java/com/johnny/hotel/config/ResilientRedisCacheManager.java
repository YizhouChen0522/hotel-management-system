package com.johnny.hotel.config;
import org.springframework.data.redis.cache.*;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/** Fail-soft display caches, including eviction callbacks executed AFTER MySQL commit. */
@Slf4j
public class ResilientRedisCacheManager extends RedisCacheManager {
    public ResilientRedisCacheManager(RedisCacheWriter writer,RedisCacheConfiguration defaults,Map<String,RedisCacheConfiguration> configurations){
        super(writer,defaults,false,configurations);
        setTransactionAware(true);
    }
    @Override protected RedisCache createRedisCache(String name,RedisCacheConfiguration configuration){
        return new DisplayCache(name,getCacheWriter(),configuration==null?getDefaultCacheConfiguration():configuration);
    }
    private static class DisplayCache extends RedisCache {
        DisplayCache(String name,RedisCacheWriter writer,RedisCacheConfiguration config){super(name,writer,config);}
        private void unavailable(){log.warn("Display cache unavailable; using MySQL and TTL-based recovery");}
        @Override protected Object lookup(Object key){try{return super.lookup(key);}catch(RuntimeException e){unavailable();return null;}}
        @Override public void put(Object key,Object value){try{super.put(key,value);}catch(RuntimeException e){unavailable();}}
        @Override public void evict(Object key){try{super.evict(key);}catch(RuntimeException e){unavailable();}}
        @Override public void clear(String pattern){try{super.clear(pattern);}catch(RuntimeException e){unavailable();}}
    }
}
