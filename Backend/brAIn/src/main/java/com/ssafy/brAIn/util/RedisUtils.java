package com.ssafy.brAIn.util;

import com.ssafy.brAIn.vote.dto.VoteResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RedisUtils {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUtils(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void setData(String key,String content,Long expireTime) {

        redisTemplate.opsForList().rightPush(key, content);
        redisTemplate.expire(key, expireTime, TimeUnit.SECONDS);
    }

    public void removeDataInList(String key,String content) {
        redisTemplate.opsForList().remove(key, 1, content);
    }

    public List<Object> getListFromKey(String key) {
        return redisTemplate.opsForList().range(key, 0, -1);
    }

    public void setSortedSet(String key, int score, String value) {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        zSetOps.add(key, value, score);
    }

    public List<Object> getSortedSet(String key) {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        Set<Object> sortedSet = zSetOps.range(key, 0, -1);
        return new ArrayList<>(sortedSet);
    }

    public Double getScoreFromSortedSet(String key, String value) {
        ZSetOperations<String, Object> zSetOperations = redisTemplate.opsForZSet();
        return zSetOperations.score(key, value);
    }

    public String getUserFromSortedSet(String key, long score) {
        ZSetOperations<String, Object> zSetOperations = redisTemplate.opsForZSet();
        Set<Object> resultSet = zSetOperations.rangeByScore(key, score, score);

        if (resultSet != null && !resultSet.isEmpty()) {
            return (String) resultSet.iterator().next();
        }
        return null;
    }

    public void updateValue(String key, Object newValue) {
        redisTemplate.opsForValue().set(key, newValue);
    }

    public void setDataInSet(String key, Object newValue,Long expireTime) {
        redisTemplate.opsForSet().add(key, newValue);
        redisTemplate.expire(key, expireTime, TimeUnit.SECONDS);

    }

    public boolean isValueInSet(String key, String value) {
        Set<Object> set = redisTemplate.opsForSet().members(key);
        if(set != null && !set.isEmpty()) {
            return set.contains(value);
        }
        return false;
    }

    public String getData(String key) {
        return (String) redisTemplate.opsForValue().get(key);
    }

    public void save(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    // 포스트잇 투표 점수 증가
    public void incrementSortedSetScore(String key, double score, String value) {
        ZSetOperations<String, Object> zSetOperations = redisTemplate.opsForZSet();
        zSetOperations.incrementScore(key, value, score);
    }

    public Set<String> keys(String pattern) {
        return redisTemplate.keys(pattern);
    }

    // 포스트잇 점수 계산
    public List<VoteResponse> getSortedSetWithScores(String key) {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        Set<ZSetOperations.TypedTuple<Object>> sortedSet = zSetOps.rangeWithScores(key, 0, -1);
        List<VoteResponse> response = sortedSet.stream()
                .map(tuple -> new VoteResponse(tuple.getValue().toString(), tuple.getScore().intValue()))
                .collect(Collectors.toList());
        log.info("key{}: {}", key, response);
        return response;
    }
}
