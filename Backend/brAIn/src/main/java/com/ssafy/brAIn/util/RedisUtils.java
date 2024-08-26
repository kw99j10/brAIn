package com.ssafy.brAIn.util;


import com.ssafy.brAIn.vote.dto.VoteResponse;
import lombok.extern.slf4j.Slf4j;

import com.ssafy.brAIn.postit.entity.PostItKey;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.beans.factory.annotation.Value;
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

    @Qualifier("redisTemplate")
    private final RedisTemplate<String, Object> redisTemplate;

    @Qualifier("redisTemplate1")
    private final RedisTemplate<String, Object> redisTemplate1;

    @Value("${spring.data.redis.expiretime}")
    private Long redisExpireTime;

    public RedisUtils(RedisTemplate<String, Object> redisTemplate, RedisTemplate<String, Object> redisTemplate1) {
        this.redisTemplate = redisTemplate;
        this.redisTemplate1 = redisTemplate1;
    }

    public void setData(String key,String content,Long expireTime) {

        redisTemplate.opsForList().rightPush(key, content);
        redisTemplate.expire(key, redisExpireTime, TimeUnit.SECONDS);
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
        redisTemplate.expire(key, redisExpireTime, TimeUnit.SECONDS);

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
        redisTemplate.expire(key, redisExpireTime, TimeUnit.SECONDS);

    }

    public void setDataInSet(String key, Object newValue,Long expireTime) {
        redisTemplate.opsForSet().add(key, newValue);
        redisTemplate.expire(key, redisExpireTime, TimeUnit.SECONDS);

    }

    public void setDataInSetSerialize(String key, Object newValue,Long expireTime) {
        redisTemplate1.opsForSet().add(key, newValue);
        redisTemplate.expire(key, redisExpireTime, TimeUnit.SECONDS);

    }

    public void removeDataInListSerialize(String key,Object content) {
        redisTemplate1.opsForSet().remove(key, 1, content);
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
        redisTemplate.expire(key, redisExpireTime, TimeUnit.SECONDS);
    }


    // 포스트잇 투표 점수 증가
    public void incrementSortedSetScore(String key, double score, String value) {
        ZSetOperations<String, Object> zSetOperations = redisTemplate.opsForZSet();
        zSetOperations.incrementScore(key, value, score);
        redisTemplate.expire(key, redisExpireTime, TimeUnit.SECONDS);

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

    // 임시 키 삭제
    public void deleteKey(String key) {
        redisTemplate.delete(key);
    }

    // 점수 삭제
    public void removeDataFromSortedSet(String key, String value) {
        ZSetOperations<String, Object> zSetOperations = redisTemplate.opsForZSet();
        zSetOperations.remove(key, value);
    }

    public boolean isValueInSortedSet(String key, String value) {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        Double score = zSetOps.score(key, value);
        return score != null;
    }


    //sortedSet에서 특정 value삭제
    public void removeValueFromSortedSet(String key, String value) {
        redisTemplate.opsForZSet().remove(key, value);
    }

    public void removeValueFromSet(String key, Object value) {
        redisTemplate.opsForSet().remove(key, value);
    }

    public Set<Object> getSetMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    public boolean isKeyExists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public Set<Object> getSetFromKey(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    public void updateSetFromKeySerialize(String key, Object newValue) {
        Set<Object> objects = redisTemplate1.opsForSet().members(key);
        if (objects != null) {
            // 2. 객체 수정
            for (Object obj : objects) {
                if (obj instanceof PostItKey) { // 객체 타입 확인
                    PostItKey myObject = (PostItKey) obj;
                    if (((PostItKey)newValue).getKey().equals(myObject.getKey())) { // 식별자로 확인
                        redisTemplate1.opsForSet().remove(key,1, obj);
                        redisTemplate1.opsForSet().add(key, newValue);
                        redisTemplate1.expire(key, redisExpireTime, TimeUnit.SECONDS);

                        break;
                    }
                }
            }
        }

    }


    public Double getLastElementFromSortedSet(String key) {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        Set<ZSetOperations.TypedTuple<Object>> result = zSetOps.reverseRangeWithScores(key, 0, 0);

        if (result != null && !result.isEmpty()) {
            ZSetOperations.TypedTuple<Object> lastElement = result.iterator().next();
            Object value = lastElement.getValue();
            Double score = lastElement.getScore();
            return score;
        } else {
            throw new RuntimeException("Sorted Set is empty or key does not exist.");
        }
    }

    public String getAINickname(String roomId) {
        String key = roomId + ":ai:nickname";
        return getData(key);
    }

    public void incr(String key) {
        redisTemplate.opsForValue().increment(key);
    }

    public Double getFirstElementFromSortedSet(String key) {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        // 정렬된 집합의 첫 번째 요소를 가져오기 위해 rangeWithScores 사용
        Set<ZSetOperations.TypedTuple<Object>> result = zSetOps.rangeWithScores(key, 0, 0);

        if (result != null && !result.isEmpty()) {
            ZSetOperations.TypedTuple<Object> firstElement = result.iterator().next();
            Object value = firstElement.getValue();
            Double score = firstElement.getScore();

            return score;
        } else {
            throw new RuntimeException("Sorted Set is empty or key does not exist.");
        }
    }


}
