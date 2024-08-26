package com.ssafy.brAIn.conferenceroom.service;

import com.ssafy.brAIn.ai.response.AIAssistant;
import com.ssafy.brAIn.ai.service.AIService;
import com.ssafy.brAIn.comment.entity.Comment;
import com.ssafy.brAIn.comment.repository.CommentRepository;
import com.ssafy.brAIn.conferenceroom.dto.ConferenceMemberRequest;
import com.ssafy.brAIn.conferenceroom.entity.ConferenceRoom;
import com.ssafy.brAIn.conferenceroom.entity.Step;
import com.ssafy.brAIn.conferenceroom.repository.ConferenceRoomRepository;
import com.ssafy.brAIn.history.dto.ConferenceToMemberResponse;
import com.ssafy.brAIn.roundpostit.entity.RoundPostIt;
import com.ssafy.brAIn.roundpostit.repository.RoundPostItRepository;
import com.ssafy.brAIn.vote.entity.Vote;
import com.ssafy.brAIn.vote.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@RequiredArgsConstructor // final이 붙거나 @NotNull이 붙은 필드의 생성자 추가
@Service // 해당 클래스를 빈으로 서블릿 컨테이너에 등록
@EnableAsync
public class ConferenceRoomService {

    private final ConferenceRoomRepository conferenceRoomRepository;

    @Autowired
    private AIService aiService;

    @Autowired
    private final VoteRepository voteRepository;

    @Autowired
    private final CommentRepository commentRepository;

    // 메모리에 회의 요약 결과를 저장할 변수
    private Map<Integer, String> meetingReportCache = new HashMap<>();
    @Autowired
    private RoundPostItRepository roundPostItRepository;

    @Transactional
    public ConferenceRoom save(ConferenceRoom conferenceRoom) {
        AIAssistant assistant = aiService.makeAIAssistant(conferenceRoom.getSubject());

        conferenceRoom.updateAi(assistant.getAssistantId(), assistant.getThreadId());
        return conferenceRoomRepository.save(conferenceRoom);
    }

    public ConferenceRoom findByInviteCode(String inviteCode) {
        return conferenceRoomRepository.findByInviteCode(inviteCode).orElse(null);
    }

    public ConferenceRoom findBySecureId(String secureId) {
        return conferenceRoomRepository.findBySecureId(secureId).orElse(null);
    }

    public ConferenceRoom findByRoomId(String roomId) {
        return conferenceRoomRepository.findById(Integer.parseInt(roomId)).orElse(null);
    }

    @Transactional
    public ConferenceMemberRequest saveConferenceHistory(ConferenceMemberRequest conferenceSaveRequest) {
        ConferenceRoom conferenceRoom = conferenceRoomRepository.findById(conferenceSaveRequest.getRoomId())
                .orElseThrow(() -> new RuntimeException("Conference Room not found"));

        // 회의 종료 시간과 시작 시간의 차이 계산
        Date totalTime = getTimeDifference(conferenceRoom.getStartTime(), new Date());

        //회의록 저장
        conferenceRoom.updateConferenceDetails(
                conferenceSaveRequest.getConclusion(),
                new Date()
        );

        // 회의 종료 처리
        conferenceRoom.updateStep(Step.STEP_5);
        ConferenceRoom updated = conferenceRoomRepository.save(conferenceRoom);

        //회원 정보
        List<ConferenceToMemberResponse> members = conferenceSaveRequest.getMembers();
        return new ConferenceMemberRequest(
                updated.getId(),
                updated.getSubject(),
                updated.getConclusion(),
                totalTime,
                members
        );
    }

    //시간 차 계산
    public static Date getTimeDifference(Date startDate, Date endDate) {
        long diffInMillis = endDate.getTime() - startDate.getTime();
        return new Date(diffInMillis - TimeZone.getDefault().getRawOffset());
    }

    // 회의룸 재설정
    public void updateConferenceRoom(Integer roomId, String subject, Date startTime) {
        ConferenceRoom conferenceRoom = conferenceRoomRepository.findById(roomId)
                .orElse(null);

        if (conferenceRoom == null) {
            throw new RuntimeException("Conference Room not found");
        } else {
            conferenceRoom.updateConferenceRoom(subject, startTime);
        }

        conferenceRoomRepository.save(conferenceRoom);
    }

    @Async
    public CompletableFuture<Void> personaMake(List<Vote> remainingIdeas, String threadId, String assistantId) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < remainingIdeas.size(); i++) {
            int finalI = i;
            CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
                // 비동기적으로 persona 값을 가져옵니다.
                return aiService.personaMake(remainingIdeas.get(finalI).getRoundPostIt().getContent(), threadId, assistantId)
                        .toFuture(); // Mono/Flux를 CompletableFuture로 변환
            }).thenCompose(personaStringFuture ->
                    personaStringFuture.thenAccept(personaString -> {
                        // personaString을 RoundPostIt에 설정합니다.
                        String textWithoutQuotes = personaString.substring(1, personaString.length() - 1);
                        RoundPostIt rp = remainingIdeas.get(finalI).getRoundPostIt();
                        rp.setPersona(textWithoutQuotes); // String으로 persona 설정
                        roundPostItRepository.save(rp);
                    })
            ).exceptionally(error -> {
                System.err.println("Error while getting persona: " + error.getMessage());
                return null;
            });

            futures.add(future);
        }

        // 모든 비동기 작업이 완료될 때까지 대기
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    @Async
    public CompletableFuture<Void> swotMake(List<Vote> remainingIdeas, String threadId, String assistantId) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < remainingIdeas.size(); i++) {
            int finalI = i;
            CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
                // 각 작업에 대한 코드 실행
                List<Comment> comments = commentRepository.findByRoundPostIt_Id(remainingIdeas.get(finalI).getId());
                List<String> commentContents = comments.stream()
                        .map(Comment::getContent)  // Comment 객체의 content 속성 추출
                        .toList();  // List<String>으로 수집

                // Mono<String>을 CompletableFuture<String>로 변환
                return aiService.swotMake(
                        remainingIdeas.get(finalI).getRoundPostIt().getContent(),
                        commentContents,
                        threadId,
                        assistantId
                ).toFuture(); // Mono를 CompletableFuture로 변환
            }).thenCompose(swotFuture ->
                    swotFuture.thenAccept(swot -> {
                        String textWithoutQuotes = swot.substring(1, swot.length() - 1);
                        // CompletableFuture가 완료된 후의 작업
                        RoundPostIt rp = remainingIdeas.get(finalI).getRoundPostIt();
                        rp.setSwot(textWithoutQuotes);
                        roundPostItRepository.save(rp);
                    })
            ).exceptionally(error -> {
                // 오류 처리
                System.err.println("Error while getting swot: " + error.getMessage());
                return null;
            });

            futures.add(future);
        }

        // 모든 비동기 작업이 완료될 때까지 대기
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }





    // 회의 결과 요약 정보
    public String generateMeetingReport(Integer roomId) throws ExecutionException, InterruptedException {
        // 이미 캐시에 존재하는 경우 캐시된 데이터를 반환
        if (meetingReportCache.containsKey(roomId)) {
            return meetingReportCache.get(roomId);
        }

        // Step 1: 특정 회의실 정보와 투표 결과 가져오기
        ConferenceRoom conferenceRoom = conferenceRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid room ID: " + roomId));
        List<Vote> voteResults = voteRepository.findByConferenceRoom_Id(roomId);

        // Step 2: 투표 결과를 점수 기준으로 상위 3개의 아이디어와 나머지 아이디어로 분리

        List<Vote> remainingIdeas = voteResults.stream()
                .sorted((v1, v2) -> v2.getScore() - v1.getScore())
                .limit(9)
                .toList();

        // 모든 아이디어를 하나의 리스트로 결합

        log.info("Thread Id: {}", conferenceRoom.getThreadId());
        log.info("Assistant Id: {}", conferenceRoom.getAssistantId());

        // Step 4: 페르소나 및 SWOT 분석 추가 (필요한 경우)

        CompletableFuture<Void> future1 = personaMake(remainingIdeas,conferenceRoom.getThreadId(), conferenceRoom.getAssistantId());
        CompletableFuture<Void> future2 = swotMake(remainingIdeas,conferenceRoom.getThreadId(), conferenceRoom.getAssistantId());

        // 두 반복문이 모두 완료될 때까지 대기
        CompletableFuture.allOf(future1, future2).get();

        // Step 5: 최종 보고서 구성
        StringBuilder reportBuilder = new StringBuilder();
        reportBuilder.append("Subject: ").append(conferenceRoom.getSubject()).append("\n\n");  // 회의 주제 추가

        // Step 6: 각 아이디어와 그에 따른 코멘트 추가


        reportBuilder.append("Other Ideas:\n\n");
        for (Vote vote : remainingIdeas) {
            RoundPostIt postIt = vote.getRoundPostIt();
            String ideaContent = postIt.getContent();
            List<Comment> participantComments = commentRepository.findByRoundPostIt_Id(postIt.getId());

            reportBuilder.append("Idea: ").append(ideaContent).append("\n");
            reportBuilder.append("Participant Comments:\n");
            for (Comment comment : participantComments) {
                reportBuilder.append(" - ").append(comment.getContent()).append("\n");
            }

            reportBuilder.append("Persona: ").append(postIt.getPersona().replace("\\n", System.lineSeparator()));
            reportBuilder.append("\n"); // 다음 아이디어와의 구분을 위해 빈 줄 추가
            reportBuilder.append("SWOT: ").append(postIt.getSwot().replace("\\n", System.lineSeparator()));
            reportBuilder.append("\n"); // 다음 아이디어와의 구분을 위해 빈 줄 추가
        }

        // Step 7: AI를 이용한 전체 요약본 생성 및 추가
        String summary = aiService.makeSummary(conferenceRoom.getThreadId(), conferenceRoom.getAssistantId()).replace("\\n", System.lineSeparator());
        reportBuilder.append("Summary:\n").append(summary).append("\n\n\n");  // AI 요약본 추가
        conferenceRoom.setConclusion(summary);
        conferenceRoomRepository.save(conferenceRoom);

        // 최종 보고서 생성 및 캐시에 저장
        String reportContent = reportBuilder.toString();
        meetingReportCache.put(roomId, reportContent);

        // Step 9: 최종 보고서 반환
        return reportContent;
    }

    // 특정 회의실의 캐시 삭제
    public void clearCacheForRoom(Integer roomId) {
        meetingReportCache.remove(roomId);
    }
}
