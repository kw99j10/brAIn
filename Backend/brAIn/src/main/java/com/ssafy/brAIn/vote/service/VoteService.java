package com.ssafy.brAIn.vote.service;

import com.ssafy.brAIn.conferenceroom.entity.ConferenceRoom;
import com.ssafy.brAIn.conferenceroom.repository.ConferenceRoomRepository;
import com.ssafy.brAIn.exception.BadRequestException;
import com.ssafy.brAIn.roundpostit.entity.RoundPostIt;
import com.ssafy.brAIn.roundpostit.repository.RoundPostItRepository;
import com.ssafy.brAIn.util.RedisUtils;
import com.ssafy.brAIn.vote.dto.FinalVoteRequest;
import com.ssafy.brAIn.vote.dto.VoteResponse;
import com.ssafy.brAIn.vote.dto.VoteResultRequest;
import com.ssafy.brAIn.vote.entity.Vote;
import com.ssafy.brAIn.vote.entity.VoteType;
import com.ssafy.brAIn.vote.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ServerErrorException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class VoteService {
    private final VoteRepository voteRepository;
    private final RoundPostItRepository roundPostItRepository;
    private final ConferenceRoomRepository conferenceRoomRepository;


    private final RedisUtils redisUtils;


    // 투표 진행 - 임시 저장
    @Transactional
    public void vote(Integer roomId, String step, Integer memberId, Map<String, Integer> votes) {
        String tempVoteKey = roomId + ":tempVotes:" + step + ":" + memberId;

        // 1점, 3점 ,5점 부여
        int score1Point = 0;
        int score3Point = 0;
        int score5Point = 0;
        for (Map.Entry<String, Integer> vote : votes.entrySet()) {
            Integer score = vote.getValue();
            if (score == 1) {
                score1Point++;
            } else if (score == 3) {
                score3Point++;
            } else if (score == 5) {
                score5Point++;
            } else {
                throw new IllegalArgumentException("Invalid score value: " + score);
            }
        }

        // 한번만 선택되어야 함
        if (score1Point > 1 || score3Point > 1 || score5Point > 1) {
            throw new IllegalArgumentException("Each score (1, 3, 5) can only be used once.");
        }


        // 기존 투표 기록 삭제
        List<Object> existingVotes = redisUtils.getSortedSet(tempVoteKey);
        for (Object postIt : existingVotes) {
            redisUtils.removeDataFromSortedSet(tempVoteKey, postIt.toString());
        }

        // 투표 갱신
        for (Map.Entry<String, Integer> vote : votes.entrySet()) {
            String postIt = vote.getKey();
            Integer newScore = vote.getValue();
            System.out.println(postIt+":"+newScore);
            redisUtils.setSortedSet(tempVoteKey, newScore, postIt);
        }
    }

    // 타이머에 의해 투표 종료
    @Transactional
    public void endVoteByTimer(VoteResultRequest voteResultRequest,Integer memberId) {
        String tempVoteKey = voteResultRequest.getConferenceId() + ":tempVotes:" + voteResultRequest.getStep() + ":"+memberId;
        String voteKey = voteResultRequest.getConferenceId() + ":votes:" + voteResultRequest.getStep();


        // 모든 사용자별 임시 데이터를 실제 키로 이동
        List<VoteResponse> tempResults = redisUtils.getSortedSetWithScores(tempVoteKey);
        for (VoteResponse result : tempResults) {
            redisUtils.incrementSortedSetScore(voteKey, result.getScore(), result.getPostIt());
        }


        redisUtils.incr(voteResultRequest.getConferenceId()+":votes:"+voteResultRequest.getStep()+":total");


        log.info("Vote Finished");
    }


    // 전체 결과를 보여줌 (상위 9개)
    @Transactional(readOnly = true)
    public List<VoteResponse> getVoteResults(Integer conferenceId, String step) {

        String key = conferenceId + ":votes:" + step;

        while(true){

            int totalUser=redisUtils.getSortedSet(conferenceId+":order:cur").size();
            int votePerson=Integer.parseInt(redisUtils.getData(conferenceId+":votes:"+step+":total"));
            if(votePerson==totalUser-1){
                break;
            }
        }

        return redisUtils.getSortedSetWithScores(key)
                .stream()
                .map(tuple -> new VoteResponse(tuple.getPostIt(), tuple.getScore()))
                .sorted((v1, v2) -> Integer.compare(v2.getScore(), v1.getScore()))
                .limit(9)
                .collect(Collectors.toList());
    }

    // 투표 결과를 DB에 저장
    @Transactional
    public void saveTop9RoundResults(List<VoteResponse> votes, VoteResultRequest voteResultRequest, Integer roomId) throws ServerErrorException {
        ConferenceRoom conferenceRoom = conferenceRoomRepository.findById(voteResultRequest.getConferenceId())
                .orElseThrow(() -> new IllegalArgumentException("잘못된 회의실 ID"));

        for (VoteResponse voteResponse : votes) {
            RoundPostIt roundPostIt = roundPostItRepository.findByContentAndConferenceRoom_Id(voteResponse.getPostIt(), voteResultRequest.getConferenceId())
                    .orElseGet(() -> {
                        log.info("Creating new RoundPostIt for content: {}", voteResponse.getPostIt());
                        return roundPostItRepository.save(
                                RoundPostIt.builder()
                                        .conferenceRoom(conferenceRoom)
                                        .content(voteResponse.getPostIt())
                                        .build()
                        );
                    });

            roundPostIt.selectedNine();

            roundPostItRepository.save(roundPostIt);

            Optional<Vote> existingVote = voteRepository.findByRoundPostItAndConferenceRoom(roundPostIt, conferenceRoom);

            // 이미 있는 투표 기록이면 반영하지 않음
            if (existingVote.isEmpty()) {
                Vote vote = Vote.builder()
                        .score(voteResponse.getScore())
                        .voteType(VoteType.MIDDLE)
                        .conferenceRoom(conferenceRoom)
                        .roundPostIt(roundPostIt)
                        .build();
                voteRepository.save(vote);
            } else {
                throw new BadRequestException("중복된 투표입니다.");
            }
        }
    }

    // Step 3

    // 선정된 9개의 투표 결과 에 대해서 다시 임시 투표 진행
    @Transactional
    public void finalVote(FinalVoteRequest request) {
        String tempVoteKey = request.getRoomId() + ":finalTempVotes:" + request.getRound()+ ":" + request.getMemberId();

        // 기존에 투표한 적이 있는지 확인
        List<Object> existingVotes = redisUtils.getSortedSet(tempVoteKey);
        for (Object existingPostIt : existingVotes) {
            redisUtils.removeDataFromSortedSet(tempVoteKey, existingPostIt.toString());
        }

        // 새로운 투표 추가
        redisUtils.setSortedSet(tempVoteKey, 1, request.getPostIt());
    }

    // 타이머 투표 진행
    @Transactional
    public void endFinalVoteByTimer(VoteResultRequest voteResultRequest) {
        String tempVotePattern = voteResultRequest.getConferenceId() + ":finalTempVotes:" + voteResultRequest.getStep() + ":*";
        String voteKey = voteResultRequest.getConferenceId() + ":finalVotes:" + voteResultRequest.getStep();

        Set<String> tempVoteKeys = redisUtils.keys(tempVotePattern);

        // 모든 사용자별 임시 데이터를 실제 키로 이동
        for (String tempVoteKey : tempVoteKeys) {
            List<VoteResponse> tempResults = redisUtils.getSortedSetWithScores(tempVoteKey);
            for (VoteResponse result : tempResults) {
                redisUtils.incrementSortedSetScore(voteKey, result.getScore(), result.getPostIt());
            }

            // 임시 데이터 삭제
            redisUtils.deleteKey(tempVoteKey);
        }

        log.info("Final vote finished");
    }

    // 전체 결과 9개 중 상위 3개를 따로 보여줌(삭제 x)
    @Transactional(readOnly = true)
    public List<VoteResponse> getFinalVoteResults(Integer conferenceId, String step) {
        String key = conferenceId + ":finalVotes:" + step;

        return redisUtils.getSortedSetWithScores(key)
                .stream()
                .map(tuple -> new VoteResponse(tuple.getPostIt(), tuple.getScore()))
                .sorted((v1, v2) -> Integer.compare(v2.getScore(), v1.getScore()))
                .limit(3) // 상위 3개의 결과만 반환
                .collect(Collectors.toList());
    }

    // 최종 투표 결과 db에 갱신
    @Transactional
    public void saveTop3FinalResults(List<VoteResponse> votes, VoteResultRequest voteResultRequest) throws ServerErrorException {
        ConferenceRoom conferenceRoom = conferenceRoomRepository.findById(voteResultRequest.getConferenceId())
                .orElseThrow(() -> new IllegalArgumentException("잘못된 회의실 ID"));

        // 새로운 점수와 결과 반영
        for (VoteResponse voteResponse : votes) {
            RoundPostIt roundPostIt = roundPostItRepository.findByContentAndConferenceRoom_Id(voteResponse.getPostIt(), voteResultRequest.getConferenceId())
                    .orElseGet(() -> {
                        log.info("Creating new RoundPostIt for content: {}", voteResponse.getPostIt());
                        return roundPostItRepository.save(
                                RoundPostIt.builder()
                                        .content(voteResponse.getPostIt())
                                        .conferenceRoom(conferenceRoom)
                                        .build()
                        );
                    });

            roundPostIt.selectedThree();
            roundPostItRepository.save(roundPostIt);
            Optional<Vote> existingVote = voteRepository.findByRoundPostItAndConferenceRoom(roundPostIt, conferenceRoom);

            //점수 업데이트
            if (existingVote.isPresent()) {
                Vote vote = existingVote.get();
                vote.updateScore(voteResponse.getScore());
                vote.updateVoteType(VoteType.FINAL);
                voteRepository.save(vote);
            }
        }
    }

    public boolean existsMiddleVoteInDB(Integer conferenceId) {
        return voteRepository.existsByConferenceRoomId(conferenceId);
    }


}
