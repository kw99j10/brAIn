package com.ssafy.brAIn.postit.service;

import com.ssafy.brAIn.auth.jwt.JWTUtilForRoom;
import com.ssafy.brAIn.postit.entity.PostItKey;
import com.ssafy.brAIn.util.RedisUtils;
import org.springframework.stereotype.Service;


@Service // 해당 클래스를 빈으로 서블릿 컨테이너에 등록
public class PostItService {

    RedisUtils redisUtils;
    JWTUtilForRoom jwtUtilForRoom;

    public PostItService(RedisUtils redisUtils, JWTUtilForRoom jwtUtilForRoom) {
        this.redisUtils = redisUtils;
        this.jwtUtilForRoom = jwtUtilForRoom;
    }


    public PostItKey postItMake(String token, String content){
        String roomId = jwtUtilForRoom.getRoomId(token);
        System.out.println(roomId);
        String nickname = jwtUtilForRoom.getNickname(token);

        System.out.println(nickname);

        String key = String.format("%s:postIt:%s",roomId,nickname);
        PostItKey postItKey = new PostItKey(content,nickname);

        redisUtils.setDataInSetSerialize(key, postItKey, 3600L);
        return postItKey;

    }

    public boolean deletePostIt(String token, PostItKey deletePostIt){
        String roomId = jwtUtilForRoom.getRoomId(token);
        String nickname = jwtUtilForRoom.getNickname(token);

        String key = String.format("%s:postIt:%s",roomId,nickname);
        try{
            redisUtils.removeDataInListSerialize(key, deletePostIt);
            return true;
        }catch (Exception e){
            return false;
        }
    }

    public boolean updatePostIt(String token, PostItKey updatePostIt){
        String roomId = jwtUtilForRoom.getRoomId(token);
        String nickname = jwtUtilForRoom.getNickname(token);

        String key = String.format("%s:postIt:%s",roomId,nickname);
        try{
            redisUtils.updateSetFromKeySerialize(key, updatePostIt);
            return true;
        }catch (Exception e){
            return false;
        }
    }
}
