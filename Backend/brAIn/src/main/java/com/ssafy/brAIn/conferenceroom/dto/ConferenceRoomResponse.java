package com.ssafy.brAIn.conferenceroom.dto;

import com.ssafy.brAIn.conferenceroom.entity.ConferenceRoom;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor // 기본 생성자 추가
@AllArgsConstructor // 모든 필드 값을 파라미터로 받는 생성자 추가
@Getter
public class ConferenceRoomResponse {
    private String inviteCode;
    private String participateUrl;
    private String jwtForRoom;
    private String secureId;
    private String subject;
    private String roomId;
    private String nickname;

    public ConferenceRoomResponse(ConferenceRoom conferenceRoom, String jwtForRoom, String nickname) {
        this.inviteCode = conferenceRoom.getInviteCode();
        this.participateUrl = conferenceRoom.getParticipateUrl();
        this.secureId = conferenceRoom.getSecureId();
        this.jwtForRoom = jwtForRoom;
        this.subject = conferenceRoom.getSubject();
        this.roomId = conferenceRoom.getId() + "";
        this.nickname = nickname;
    }
}
