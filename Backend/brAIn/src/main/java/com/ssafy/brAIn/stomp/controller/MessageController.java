package com.ssafy.brAIn.stomp.controller;

import com.ssafy.brAIn.ai.service.AIService;
import com.ssafy.brAIn.auth.jwt.JWTUtilForRoom;
import com.ssafy.brAIn.conferenceroom.entity.ConferenceRoom;
import com.ssafy.brAIn.conferenceroom.entity.Step;
import com.ssafy.brAIn.conferenceroom.service.ConferenceRoomService;
import com.ssafy.brAIn.roundpostit.entity.RoundPostIt;
import com.ssafy.brAIn.roundpostit.service.RoundPostItService;
import com.ssafy.brAIn.stomp.dto.*;
import com.ssafy.brAIn.stomp.request.*;
import com.ssafy.brAIn.stomp.response.*;
import com.ssafy.brAIn.stomp.service.MessageService;
import com.ssafy.brAIn.util.RedisUtils;
import com.ssafy.brAIn.vote.dto.VoteResponse;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.ArrayList;
import java.util.List;


@Controller
public class MessageController {

    private final RabbitTemplate rabbitTemplate;
    private final MessageService messageService;

    private final JWTUtilForRoom jwtUtilForRoom;
    private final AIService aiService;
    private final ConferenceRoomService conferenceRoomService;
    private final RedisUtils redisUtils;
    private final RoundPostItService roundPostItService;

    public MessageController(RabbitTemplate rabbitTemplate,
                             MessageService messageService,
                             JWTUtilForRoom jwtUtilForRoom,
                             AIService aiService, ConferenceRoomService conferenceRoomService, RedisUtils redisUtils, RoundPostItService roundPostItService) {
        this.rabbitTemplate = rabbitTemplate;
        this.messageService = messageService;
        this.jwtUtilForRoom = jwtUtilForRoom;
        this.aiService = aiService;
        this.conferenceRoomService = conferenceRoomService;
        this.redisUtils = redisUtils;
        this.roundPostItService = roundPostItService;
    }


    //유저 답변 제출완료(테스트 완)
    //유저가 답변을 제출하면 자동으로 다음 사람으로 넘어가야 함.
    @MessageMapping("step1.submit.{roomId}")
    public void submitPost(RequestGroupPost groupPost, @DestinationVariable String roomId,StompHeaderAccessor accessor) {

        String token=accessor.getFirstNativeHeader("Authorization");
        String nickname=jwtUtilForRoom.getNickname(token);

        String curUser=messageService.getCurUser(Integer.parseInt(roomId));
        if(!curUser.equals(nickname)) {
            throw new AuthenticationCredentialsNotFoundException("자신의 차례에만 제출할 수 있습니다.");
        }
        ConferenceRoom cr = conferenceRoomService.findByRoomId(roomId);
        aiService.addPostIt(groupPost.getContent(), cr.getThreadId());


        messageService.sendPost(Integer.parseInt(roomId),groupPost,nickname);
        ResponseGroupPost responseGroupPost=makeResponseGroupPost(groupPost,Integer.parseInt(roomId),nickname);
        rabbitTemplate.convertAndSend("amq.topic","room." + roomId, responseGroupPost);

        //끝나면 종료
        if(responseGroupPost.getMessageType().equals(MessageType.SUBMIT_POST_IT_AND_END))return;


        //만약 다음 사람이 ai라면 추가적인 로직 필요
        String nextUser=messageService.NextOrder(Integer.parseInt(roomId),nickname);

        boolean curUserIsLast=messageService.isLastOrder(Integer.parseInt(roomId),nickname);

        //다음 사람이 ai가 아니라면 종료
        if(!messageService.isAi(Integer.parseInt(roomId),nextUser))return;
        String aiPostIt=messageService.receiveAImessage(Integer.parseInt(roomId));
        System.out.println("aiPostIt:"+aiPostIt);

        RequestGroupPost aiGroupPost=null;
        if (curUserIsLast) {
            aiGroupPost=new RequestGroupPost(groupPost.getRound()+1,aiPostIt);
        }else{
            aiGroupPost=new RequestGroupPost(groupPost.getRound(),aiPostIt);
        }

        messageService.sendPost(Integer.parseInt(roomId),aiGroupPost,nextUser);

        ResponseGroupPost aiResponseGroupPost=makeResponseGroupPost(aiGroupPost,Integer.parseInt(roomId),nextUser);
        rabbitTemplate.convertAndSend("amq.topic","room." + roomId, aiResponseGroupPost);

    }

    private ResponseGroupPost makeResponseGroupPost(RequestGroupPost groupPost,Integer roomId,String nickname) {
        String nextUser=messageService.NextOrder(roomId,nickname);
        messageService.updateCurOrder(roomId,nextUser);
        if (messageService.isLastOrder(roomId, nickname)) {
            System.out.println("마지막 사람만 이곳에 와야한다.");

            if (messageService.isStep1EndCondition(roomId)) {
                messageService.updateStep(roomId,Step.STEP_2);
                messageService.initUserState(roomId);
                return new ResponseGroupPost(MessageType.SUBMIT_POST_IT_AND_END,nickname,null,groupPost.getRound(), groupPost.getRound()+1, groupPost.getContent());
            }
                return new ResponseGroupPost(MessageType.SUBMIT_POST_IT,nickname,nextUser,groupPost.getRound(), groupPost.getRound()+1, groupPost.getContent());
        }
        return new ResponseGroupPost(MessageType.SUBMIT_POST_IT,nickname,nextUser,groupPost.getRound(), groupPost.getRound(), groupPost.getContent());
    }

    //대기방에서 회의방 시작하기(테스트 완)(아직 secured는 테스트 못함)

    @MessageMapping("start.conferences.{roomId}")
    public void startConference(@DestinationVariable String roomId, StompHeaderAccessor accessor)  {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        String role=jwtUtilForRoom.getRole(authorization);
        System.out.println(role);
        if (!role.equals("CHIEF")) {
            throw new AuthenticationCredentialsNotFoundException("권한이 없음");
        }
        System.out.println(roomId);
        List<String> users=messageService.startConferences(Integer.parseInt(roomId)).stream()
                .map(Object::toString)
                .toList();

        for(String user:users){
            System.out.println(user);
        }

        //초기화
        messageService.initUserState(Integer.parseInt(roomId));

        //0단계 부터  시작.
        ConferenceRoom conferenceRoom = conferenceRoomService.findByRoomId(roomId).updateStep(Step.STEP_0);
        conferenceRoomService.save(conferenceRoom);

        // Redis에서 AI 닉네임 가져오기
        String aiNickname = redisUtils.getAINickname(roomId);

        // AI 닉네임을 로그로 확인하거나, 필요시 다른 로직에 사용
        System.out.println("AI Nickname: " + aiNickname);


        rabbitTemplate.convertAndSend("amq.topic","room."+roomId,new StartMessage(MessageType.START_CONFERENCE,users,aiNickname));

    }

    //회의 다음단계 시작(테스트 완)(Secured미완)

    @MessageMapping("next.step.{roomId}")
    public void nextStep(@Payload RequestStep requestStep, @DestinationVariable String roomId,StompHeaderAccessor accessor) {

        String authorization = accessor.getFirstNativeHeader("Authorization");
        String role=jwtUtilForRoom.getRole(authorization);
        if (!role.equals("CHIEF")) {
            throw new AuthenticationCredentialsNotFoundException("권한이 없음");
        }

        Step nextStep=requestStep.getStep().next();
        rabbitTemplate.convertAndSend("amq.topic","room."+roomId,new ResponseStep(MessageType.NEXT_STEP,nextStep));
        messageService.updateStep(Integer.parseInt(roomId),nextStep);
    }

    //유저 준비 완료
    @MessageMapping("state.user.ready.{roomId}")
    public void readyState(@DestinationVariable String roomId, StompHeaderAccessor accessor) {
        String token=accessor.getFirstNativeHeader("Authorization");
        String nickname=jwtUtilForRoom.getNickname(token);

        messageService.updateUserState(Integer.parseInt(roomId), nickname, UserState.READY);

        // Redis에서 AI 닉네임 가져오기
        String aiNickname = redisUtils.getAINickname(roomId);

        rabbitTemplate.convertAndSend("amq.topic","room."+roomId,new ResponseUserState(UserState.READY, nickname, aiNickname));
    }

    //유저 답변 패스(테스트 완)
    @MessageMapping("state.user.pass.{roomId}")
    public void passRound(@DestinationVariable String roomId, StompHeaderAccessor accessor, RequestPass pass) {
        String token=accessor.getFirstNativeHeader("Authorization");
        String nickname=jwtUtilForRoom.getNickname(token);

        messageService.updateUserState(Integer.parseInt(roomId),nickname,UserState.PASS);
        String nextMember=messageService.NextOrder(Integer.parseInt(roomId),nickname);

        //현재 유저가 라운드의 마지막 유저라면
        if (messageService.isLastOrder(Integer.parseInt(roomId),nickname)) {
            //종료 조건이라면
            System.out.println("마지막 사람 패스했을 때, 종료조건 만족?:"+messageService.isStep1EndCondition(Integer.parseInt(roomId)));
            if (messageService.isStep1EndCondition(Integer.parseInt(roomId))) {
                System.out.println("패스 후 종료");
                messageService.updateStep(Integer.parseInt(roomId),Step.STEP_2);
                rabbitTemplate.convertAndSend("amq.topic","room."+roomId,new ResponsePassAndEnd(MessageType.PASS_AND_END,nickname));
                messageService.initUserState(Integer.parseInt(roomId));
                return;
            }
            messageService.initUserState(Integer.parseInt(roomId));
            messageService.updateCurOrder(Integer.parseInt(roomId),nextMember);
            rabbitTemplate.convertAndSend("amq.topic","room."+roomId,new ResponseRoundState(UserState.PASS,nickname,nextMember, pass.getCurRound()+1));
            return;
        }
        messageService.updateCurOrder(Integer.parseInt(roomId),nextMember);
        rabbitTemplate.convertAndSend("amq.topic","room."+roomId,new ResponseRoundState(UserState.PASS,nickname,nextMember, pass.getCurRound()));

        //다음 사람이 ai가 아니라면 종료
        if(!messageService.isAi(Integer.parseInt(roomId),nextMember))return;
        String aiPostIt=messageService.receiveAImessage(Integer.parseInt(roomId));

        boolean curUserIsLast=messageService.isLastOrder(Integer.parseInt(roomId),nickname);

        RequestGroupPost aiGroupPost=null;
        if (curUserIsLast) {
            aiGroupPost=new RequestGroupPost(pass.getCurRound()+1,aiPostIt);
        }else{
            aiGroupPost=new RequestGroupPost(pass.getCurRound(),aiPostIt);
        }

        messageService.sendPost(Integer.parseInt(roomId),aiGroupPost,nextMember);
        //messageService.updateUserState(Integer.parseInt(roomId),nickname,UserState.SUBMIT);

        ResponseGroupPost aiResponseGroupPost=makeResponseGroupPost(aiGroupPost,Integer.parseInt(roomId),nextMember);
        rabbitTemplate.convertAndSend("amq.topic","room." + roomId, aiResponseGroupPost);

    }

    //타이머 시간 추가
    @MessageMapping("timer.modify.{roomId}")
    public void modifyTimer(@DestinationVariable String roomId, @Payload Long time, StompHeaderAccessor accessor) {
        String token=accessor.getFirstNativeHeader("Authorization");
        String sender=jwtUtilForRoom.getNickname(token);
        String curUser=messageService.getCurUser(Integer.parseInt(roomId));
        if(!sender.equals(curUser))return;
        rabbitTemplate.convertAndSend("amq.topic","room."+roomId,new Timer(MessageType.PLUS_TIME,time));
    }


    // 현재 중간 투표 결과 반환
    // 현재 중간 투표 결과 (상위 9개) 반환
    // 유저마다 다른 순서로 감
    @MessageMapping("vote.middleResults.{roomId}.{step}")
    public void getMiddleVoteResults(@DestinationVariable String roomId, @DestinationVariable String step, StompHeaderAccessor accessor) {
        String token = accessor.getFirstNativeHeader("Authorization");
        String nickName = jwtUtilForRoom.getNickname(token);
        ConferenceRoom conferenceRoom = conferenceRoomService.findByRoomId(roomId);

        // 중간 투표 결과 가져오기
        ResponseMiddleVote voteResults = messageService.getMiddleVote(Integer.parseInt(roomId), step);

        List<VoteResponse> votes=voteResults.getVotes();

        for(int i=0;i<votes.size();i++){
            System.out.println(votes.get(i).getPostIt());
        }

        List<String> usersInRoom = messageService.getUsersInRoom(Integer.parseInt(roomId));
        String ai=messageService.getAI(Integer.parseInt(roomId));
        for(int i=0;i<usersInRoom.size();i++){
            if(usersInRoom.get(i).equals(ai))continue;
            List<String> step3ForUser=new ArrayList<>();
            for(int j=0;j<votes.size();j++){
                step3ForUser.add(votes.get((i+j)%votes.size()).getPostIt());
            }
            Step3ForUser step3ForUserResponse=new Step3ForUser(MessageType.STEP3_FOR_USER,step3ForUser);
            System.out.println(usersInRoom.get(i));
            for(int j=0;j<step3ForUser.size();j++){
                System.out.print(step3ForUser.get(j)+" ");
            }
            System.out.println();
            rabbitTemplate.convertAndSend("room."+roomId+"."+usersInRoom.get(i),step3ForUserResponse);
        }



    }


    // 현재 최종 투표 결과 반환
    // 현재 최종 투표 결과 (상위 3개) 반환
    @MessageMapping("vote.finalResults.{roomId}.{round}") // (Send Topic 매핑)
    public void getFinalVoteResults(@DestinationVariable String roomId, @DestinationVariable Integer round, StompHeaderAccessor accessor) {
        String token = accessor.getFirstNativeHeader("Authorization");
        String nickName = jwtUtilForRoom.getNickname(token);
        ConferenceRoom conferenceRoom = conferenceRoomService.findByRoomId(roomId);

        // 최종 투표 결과 가져오기
        ResponseMiddleVote voteResults = messageService.getFinalVote(Integer.parseInt(roomId), round);
        // 결과를 RabbitMQ로 전송(Subscribe)
        rabbitTemplate.convertAndSend("amq.topic", "room." + roomId, voteResults);
    }

    // 최종 결과물 반환 (임시)


    @MessageMapping("next.idea.{roomId}")
    public void nextIdea(@DestinationVariable String roomId, StompHeaderAccessor accessor, @RequestBody CurIndex curIndex) {
        String token = accessor.getFirstNativeHeader("Authorization");

        List<RoundPostIt> roundPostIts=roundPostItService.findByRoomId(Integer.parseInt(roomId)).stream()
                        .filter(RoundPostIt::isLast9).toList();

        if(roundPostIts.size()-1==curIndex.getCurIndex()){
            rabbitTemplate.convertAndSend("amq.topic","room."+roomId,new NextIdea(MessageType.END_IDEA));
            return;
        }
        rabbitTemplate.convertAndSend("amq.topic","room."+roomId,new NextIdea(MessageType.NEXT_IDEA));

    }

    @MessageMapping("get.aiIdea.{roomId}")
    public void getAiIdea(@DestinationVariable String roomId, RequestAi requestAi) {

        System.out.println("ai가 메시지 보냄?");
        String aiPostIt=messageService.receiveAImessage(Integer.parseInt(roomId));
        System.out.println("aiPostIt:"+aiPostIt);

        String ai=messageService.getAI(Integer.parseInt(roomId));
        RequestGroupPost aiGroupPost=null;
        aiGroupPost=new RequestGroupPost(requestAi.getRound(),aiPostIt);


        messageService.sendPost(Integer.parseInt(roomId),aiGroupPost,ai);

        ResponseGroupPost aiResponseGroupPost=makeResponseGroupPost(aiGroupPost,Integer.parseInt(roomId),ai);
        rabbitTemplate.convertAndSend("amq.topic","room." + roomId, aiResponseGroupPost);
    }
}
