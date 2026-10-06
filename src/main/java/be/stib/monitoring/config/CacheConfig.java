package be.stib.monitoring.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String TRAVELLERS_INFORMATION = "travellersInformation";
    public static final String STOPS_BY_LINE = "stopsByLine";
    public static final String STOP_DETAILS = "stopDetails";
    public static final String VEHICLE_POSITIONS = "vehiclePositions";
    /** Keyed by stop id. */
    public static final String WAITING_TIMES = "waitingTimes";

    @SuppressWarnings("null")
    @Bean
    CacheManager cacheManager(StibProperties properties) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache(TRAVELLERS_INFORMATION, cache(properties.cache().messagesTtl(), 1));
        manager.registerCustomCache(STOPS_BY_LINE, cache(properties.cache().staticTtl(), 500));
        manager.registerCustomCache(STOP_DETAILS, cache(properties.cache().staticTtl(), 500));
        manager.registerCustomCache(VEHICLE_POSITIONS, cache(properties.cache().liveTtl(), 1));
        manager.registerCustomCache(WAITING_TIMES, cache(properties.cache().liveTtl(), 10_000));
        return manager;
    }

    private static com.github.benmanes.caffeine.cache.Cache<Object, Object> cache(Duration ttl, long maxSize) {
        return Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build();
    }
}
