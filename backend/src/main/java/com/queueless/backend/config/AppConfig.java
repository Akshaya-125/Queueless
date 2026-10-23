package com.queueless.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class AppConfig {

    /** "Today" for daily token numbering is decided in this time zone (default: India). */
    @Bean
    public Clock clock(@Value("${queueless.timezone:Asia/Kolkata}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
