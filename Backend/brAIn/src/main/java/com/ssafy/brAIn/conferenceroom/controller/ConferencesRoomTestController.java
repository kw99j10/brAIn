package com.ssafy.brAIn.conferenceroom.controller;

import com.ssafy.brAIn.auth.jwt.JWTUtilForRoom;
import com.ssafy.brAIn.conferenceroom.dto.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/test")
public class ConferencesRoomTestController {

    @Autowired
    private JWTUtilForRoom jwtUtilForRoom;

    @PostMapping("/create")
    public ResponseEntity<?> createRoom(@RequestBody Test test) {
        System.out.println(test.getRoomId());
        String accessToken=jwtUtilForRoom.createJwt(test.getCategory(), test.getUsername(), test.getRole(),
                test.getNickname(),test.getRoomId(), 100000000000L);

        return ResponseEntity.ok(Map.of("accessToken", accessToken));
    }


}
