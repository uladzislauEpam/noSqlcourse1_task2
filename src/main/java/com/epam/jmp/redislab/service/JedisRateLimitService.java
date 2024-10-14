package com.epam.jmp.redislab.service;

import com.epam.jmp.redislab.configuration.ratelimit.RateLimitRule;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JedisRateLimitService implements RateLimitService{

    private final List<RateLimitRule> rateLimitRules;

    public JedisRateLimitService(List<RateLimitRule> rateLimitRules) {
        this.rateLimitRules = rateLimitRules;
    }
}
