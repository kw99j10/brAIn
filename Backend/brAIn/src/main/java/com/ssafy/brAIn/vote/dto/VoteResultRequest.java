package com.ssafy.brAIn.vote.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class VoteResultRequest {
    Integer conferenceId;
    String step;
}
