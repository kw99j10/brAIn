package com.ssafy.brAIn.vote.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class VoteRequest {

    private int roomId;
    private String step;
    //private int memberId;
    private Map<String,Integer> votes;
}
