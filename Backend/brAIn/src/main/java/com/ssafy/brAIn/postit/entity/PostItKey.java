package com.ssafy.brAIn.postit.entity;

import com.ssafy.brAIn.util.HashGenerator;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PostItKey {

    private String message;
    private String key;

    public PostItKey(String message, String nick) {
        this.message = message;
        this.key = HashGenerator.generateHash(nick);
    }
}
