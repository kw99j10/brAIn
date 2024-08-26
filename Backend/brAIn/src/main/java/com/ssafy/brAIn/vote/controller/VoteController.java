package com.ssafy.brAIn.vote.controller;

import com.ssafy.brAIn.auth.jwt.JWTUtilForRoom;
import com.ssafy.brAIn.member.entity.Member;
import com.ssafy.brAIn.member.service.MemberService;
import com.ssafy.brAIn.vote.dto.FinalVoteRequest;
import com.ssafy.brAIn.vote.dto.VoteRequest;
import com.ssafy.brAIn.vote.dto.VoteResponse;
import com.ssafy.brAIn.vote.dto.VoteResultRequest;
import com.ssafy.brAIn.vote.service.VoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/conferences/vote")
public class VoteController {

    private final VoteService voteService;
    private final JWTUtilForRoom jwtUtilForRoom;
    private final MemberService memberService;

    // 투표 결정(진행)
    @PostMapping
    public ResponseEntity<String> vote(@RequestBody VoteRequest voteRequest, @RequestHeader HttpHeaders headers) {
        String token = headers.getFirst("AuthorizationRoom");
        if(token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        System.out.println(token);
        String email=jwtUtilForRoom.getUsername(token);
        System.out.println(email);
        Optional<Member> member=memberService.findByEmail(email);
        if(member.isEmpty()) {
            throw new IllegalArgumentException("일치하는 회원 없음");
        }
        Integer memberId=member.get().getId();

        System.out.println(voteRequest.getVotes().size());
        voteService.vote(voteRequest.getRoomId(), voteRequest.getStep(), memberId, voteRequest.getVotes());
        return new ResponseEntity<>("Vote successful", HttpStatus.OK);
    }

    // 타이머에 의해 투표 종료
    @PostMapping("/endByTimer")
    public ResponseEntity<?> endVoteByTimer(@RequestBody VoteResultRequest voteResultRequest,@RequestHeader HttpHeaders headers) {
        String token = headers.getFirst("AuthorizationRoom");
        if(token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        Optional<Member> member = memberService.findByEmail(jwtUtilForRoom.getUsername(token));
        voteService.endVoteByTimer(voteResultRequest,member.get().getId());
        return new ResponseEntity<>("Vote Finished", HttpStatus.OK);
    }

    // 투표 결과 집계
    @GetMapping("/results")
    public ResponseEntity<List<VoteResponse>> voteResults(@RequestParam Integer roomId, @RequestParam String step, @RequestHeader HttpHeaders headers) {
        String token = headers.getFirst("AuthorizationRoom");
        if(token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        List<VoteResponse> results = voteService.getVoteResults(roomId, step);
        for(VoteResponse voteResponse: results) {
            System.out.println(voteResponse);
        }
        VoteResultRequest voteResultRequest = new VoteResultRequest(roomId, step);
        if(jwtUtilForRoom.getRole(token).equals("CHIEF")){
            voteService.saveTop9RoundResults(results, voteResultRequest, roomId);
        }
        return ResponseEntity.ok().body(results);
    }

    // Step 3

    // 선정된 9개의 투표 결과 에 대해서 다시 임시 투표 진행
    @PostMapping("/finalVote")
    public ResponseEntity<String> finalVote(@RequestBody FinalVoteRequest finalVoteRequest) {
        voteService.finalVote(finalVoteRequest);
        return new ResponseEntity<>("Final vote successful", HttpStatus.OK);
    }

    // 최종 투표 종료
    @PostMapping("/endFinalVoteByTimer")
    public ResponseEntity<?> endFinalVoteByTimer(@RequestBody VoteResultRequest voteResultRequest) {
        voteService.endFinalVoteByTimer(voteResultRequest);
        return new ResponseEntity<>("Final vote finished", HttpStatus.OK);
    }

    // 최종 투표 결과 집계
    @GetMapping("/finalResults")
    public ResponseEntity<List<VoteResponse>> getFinalVoteResults(@RequestParam Integer roomId, @RequestParam String step) {
        List<VoteResponse> results = voteService.getFinalVoteResults(roomId, step);
        return ResponseEntity.ok().body(results);
    }

    // 최종 투표 결과 db 저장
    @PostMapping("/saveFinalResults")
    public ResponseEntity<String> saveFinalResults(@RequestBody VoteResultRequest voteResultRequest) {
        List<VoteResponse> results = voteService.getFinalVoteResults(voteResultRequest.getConferenceId(), voteResultRequest.getStep());
        voteService.saveTop3FinalResults(results, voteResultRequest);
        return new ResponseEntity<>("Final vote results saved successfully", HttpStatus.OK);
    }

    // 투표 결과를을 담은 리스트를 소켓에 담아 메시지 보내기 -> Message Controller
}
