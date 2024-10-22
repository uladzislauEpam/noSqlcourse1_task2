package com.epam.jmp.redislab.service;

import com.epam.jmp.redislab.api.RequestDescriptor;
import com.epam.jmp.redislab.configuration.RateLimitConfiguration;
import com.epam.jmp.redislab.configuration.ratelimit.RateLimitRule;
import com.epam.jmp.redislab.configuration.ratelimit.RateLimitTimeInterval;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisCluster;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Component
public class JedisRateLimitService implements RateLimitService{

    @Autowired
    RateLimitConfiguration rateLimitConfiguration;

    @Autowired
    JedisCluster jedisCluster;

    private List<RateLimitRule> rateLimitRules;

    public JedisRateLimitService(List<RateLimitRule> rateLimitRules) {
        this.rateLimitRules = rateLimitRules;
    }

    //IMPLEMENTING BALANCER
    @Override
    public boolean shouldLimit(Set<RequestDescriptor> requestDescriptors) {

        try {
            rateLimitRules = rateLimitConfiguration.rateLimitRules().stream().toList();
            for (RequestDescriptor requestDescriptor : requestDescriptors) {
                RateLimitRule rule = getCorrespondingRule(requestDescriptor, rateLimitRules);
                if(shouldLimitDescriptor(requestDescriptor, rule, jedisCluster)) {
                    return true;
                }
            }
            for (RequestDescriptor requestDescriptor : requestDescriptors) {
                long now = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();
                int hash = requestDescriptor.hashCode();
                jedisCluster.zadd(String.valueOf(hash), now, hash + ":" + now);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
        return false;
    }

    private RateLimitRule getCorrespondingRule(RequestDescriptor descriptor, List<RateLimitRule> rules) {
        Optional<RateLimitRule> strictRule = rules.stream().filter(rule -> rule.getAccountId().orElse("").equals(descriptor.getAccountId().orElse("")) &&
                        rule.getClientIp().orElse("").equals(descriptor.getClientIp().orElse("")) &&
                        rule.getRequestType().orElse("").equals(descriptor.getRequestType().orElse("")))
                .findFirst();
        return strictRule.orElseGet(() -> rules.stream()
                .filter(rule ->
                        (rule.getAccountId().orElse("").equals(descriptor.getAccountId().orElse("")) || rule.getAccountId().orElse("1").isEmpty()) &&
                        (rule.getClientIp().orElse("").equals(descriptor.getClientIp().orElse("")) || rule.getClientIp().orElse("1").isEmpty()) &&
                        (rule.getRequestType().orElse("").equals(descriptor.getRequestType().orElse("")) || rule.getRequestType().orElse("1").isEmpty()))
                .findFirst().orElseThrow());

    }

    private boolean shouldLimitDescriptor(RequestDescriptor descriptor, RateLimitRule rule, JedisCluster jedisCluster) {
        return rule.getAllowedNumberOfRequests() == getCurrentlyAllowedRequestsNumber(descriptor, jedisCluster, rule);
    }

    private int getCurrentlyAllowedRequestsNumber(RequestDescriptor descriptor, JedisCluster jedisCluster, RateLimitRule rule) {
        long now = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();
        int hash = descriptor.hashCode();
        if(rule.getTimeInterval() == RateLimitTimeInterval.HOUR) {
            return jedisCluster.zrangeByScore(String.valueOf(hash), now - 3600 * 1000L, now).size();
        } else {
            return jedisCluster.zrangeByScore(String.valueOf(hash), now - 60 * 1000L, now).size();
        }
    }

}
