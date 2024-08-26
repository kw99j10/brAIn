package com.ssafy.brAIn.stomp.response;


import com.ssafy.brAIn.stomp.dto.UserState;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ResponseUserState {

    private UserState messageType;
    private String curUser;
    private String aiNickname;

    public ResponseUserState(UserState type, String curUser, String aiNickname) {
        this.messageType = type;
        this.curUser = curUser;
        this.aiNickname = aiNickname;
    }
}
