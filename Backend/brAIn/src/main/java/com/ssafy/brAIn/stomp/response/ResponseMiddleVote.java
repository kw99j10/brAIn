package com.ssafy.brAIn.stomp.response;

import com.ssafy.brAIn.stomp.dto.MessageType;
import com.ssafy.brAIn.vote.dto.VoteResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ResponseMiddleVote {
    private MessageType messageType;
    private List<VoteResponse> votes;

    public ResponseMiddleVote(MessageType type, List<VoteResponse> votes) {
        this.messageType = type;
        this.votes = votes;
    }
}
