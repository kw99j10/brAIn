package com.ssafy.brAIn.stomp.config;

import com.ssafy.brAIn.auth.jwt.JWTUtilForRoom;
import com.ssafy.brAIn.conferenceroom.entity.ConferenceRoom;
import com.ssafy.brAIn.conferenceroom.entity.Step;
import com.ssafy.brAIn.conferenceroom.repository.ConferenceRoomRepository;
import com.ssafy.brAIn.conferenceroom.service.ConferenceRoomService;
import com.ssafy.brAIn.history.entity.MemberHistory;
import com.ssafy.brAIn.history.entity.MemberHistoryId;
import com.ssafy.brAIn.history.model.Role;
import com.ssafy.brAIn.history.model.Status;
import com.ssafy.brAIn.history.repository.MemberHistoryRepository;
import com.ssafy.brAIn.member.entity.Member;
import com.ssafy.brAIn.member.repository.MemberRepository;
import com.ssafy.brAIn.member.service.MemberService;
import com.ssafy.brAIn.stomp.dto.MessageType;
import com.ssafy.brAIn.stomp.dto.WaitingRoomEnterExit;
import com.ssafy.brAIn.stomp.response.ConferencesEnterExit;
import com.ssafy.brAIn.stomp.response.EndMessage;
import com.ssafy.brAIn.stomp.service.MessageService;
import com.ssafy.brAIn.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class WebSocketEventListener {


    private final ConferenceRoomRepository conferenceRoomRepository;
    private final RedisUtils redisUtils;
    private final JWTUtilForRoom jwtUtilForRoom;
    private final MemberHistoryRepository memberHistoryRepository;
    private final MemberRepository memberRepository;
    private final RabbitTemplate rabbitTemplate;
    private final MessageService messageService;
    private final MemberService memberService;
    private final ConferenceRoomService conferenceRoomService;

    public WebSocketEventListener(ConferenceRoomRepository conferenceRoomRepository,
                                  RedisUtils redisUtils,
                                  JWTUtilForRoom jwtUtilForRoom,
                                  MemberRepository memberRepository,
                                  MemberHistoryRepository memberHistoryRepository, @Qualifier("rabbitTemplate") RabbitTemplate rabbitTemplate, MessageService messageService, MemberService memberService, ConferenceRoomService conferenceRoomService) {
        this.conferenceRoomRepository = conferenceRoomRepository;
        this.redisUtils = redisUtils;
        this.jwtUtilForRoom = jwtUtilForRoom;
        this.memberRepository = memberRepository;
        this.memberHistoryRepository = memberHistoryRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.messageService = messageService;
        this.memberService = memberService;
        this.conferenceRoomService = conferenceRoomService;
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String token = accessor.getFirstNativeHeader("Authorization");

        System.out.println("최초연결 시:"+token);
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);

            String email = jwtUtilForRoom.getUsername(token);
            Integer roomId = Integer.parseInt(jwtUtilForRoom.getRoomId(token));
            String nickname=jwtUtilForRoom.getNickname(token);
            System.out.println(nickname);
            Integer memberId = getMemberId(email);
            Role role = Role.valueOf(jwtUtilForRoom.getRole(token));
            Optional<Member> member = memberRepository.findByEmail(email);
            Optional<ConferenceRoom> room = conferenceRoomRepository.findById(roomId);

            if (member.isEmpty() || room.isEmpty()) {
                //오류
                return;
            }
            MemberHistoryId memberHistoryId = new MemberHistoryId(memberId, roomId);

            MemberHistory memberHistory = MemberHistory.builder().id(memberHistoryId)
                    .role(role)
                    .status(Status.COME)
                    .nickName(jwtUtilForRoom.getNickname(token))
                    .member(member.get())
                    .conferenceRoom(room.get())
                    .build();


            Optional<MemberHistory> optionalMemberHistory = null;
            if (memberHistory != null) {

                System.out.println("User connected: " + memberHistory.getUsername());

                optionalMemberHistory = memberHistoryRepository.findById(memberHistoryId);
                //최초 입장시
                if (optionalMemberHistory.isEmpty()) {
                    memberHistoryRepository.save(memberHistory);
                    rabbitTemplate.convertAndSend("amq.topic","room."+roomId,new WaitingRoomEnterExit(MessageType.ENTER_WAITING_ROOM));
                } else {    //중간 입장 시,
                    optionalMemberHistory.get().historyStateUpdate(Status.COME);
                    memberHistoryRepository.save(optionalMemberHistory.get());

                    if (room.get().getStep().equals(Step.WAIT)) {
                        rabbitTemplate.convertAndSend("amq.topic","room."+roomId,new WaitingRoomEnterExit(MessageType.ENTER_WAITING_ROOM));
                    }else{
                        redisUtils.setSortedSet(roomId + ":order:cur", optionalMemberHistory.get().getOrders(),optionalMemberHistory.get().getNickName());

                        List<String> usersInRoom = messageService.getUsersInRoom(roomId);
                        rabbitTemplate.convertAndSend("amq.topic","room."+roomId,new ConferencesEnterExit(MessageType.ENTER_CONFERENCES, jwtUtilForRoom.getNickname(token),null,false,false,usersInRoom));
                    }
                }
            }

            if (redisUtils.isValueInSet(roomId + ":out", jwtUtilForRoom.getNickname(token))) {
                redisUtils.removeValueFromSet(roomId + ":out", jwtUtilForRoom.getNickname(token));
            }


            //레디스에 sessionId와 함께 닉네임을 저장해서 갑작스러운 종료 때, 닉네임을 얻기 위함.
            String sessionId = accessor.getSessionId();
            redisUtils.save(sessionId, memberId + ":" + roomId);

        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        // Redis에서 데이터 가져오기
        String data = redisUtils.getData(sessionId);

        // Redis에서 정보가 없으면 그냥 종료
        if (data == null || data.isEmpty()) {
            System.out.println("세션 ID: " + sessionId + "에 대한 Redis 데이터가 없습니다.");
            return;
        }

        try {
            // 데이터 분리
            String[] historyId = data.split(":");
            Integer memberId = Integer.parseInt(historyId[0]);
            Integer roomId = Integer.parseInt(historyId[1]);

            // Member 정보 가져오기
            Optional<Member> member = memberService.findById(memberId);
            if (!member.isPresent()) {
                System.out.println("회원 ID: " + memberId + "에 대한 정보가 없습니다.");
                return;
            }

            String email = member.get().getEmail();

            // 메시지 서비스 업데이트
            messageService.historyUpdate(roomId, email);

            // ConferenceRoom 정보 가져오기
            ConferenceRoom conferenceRoom = conferenceRoomService.findByRoomId(roomId.toString());

            // 사용자 닉네임 가져오기
            String exitUserNickname = getNickName(memberId, roomId);

            log.info("user disconnected: {}" , exitUserNickname);
            //방장이 나가면 방 종료.
            if(getRole(memberId,roomId).equals(Role.CHIEF)){
                rabbitTemplate.convertAndSend("amq.topic","room."+roomId,new EndMessage(MessageType.END_CONFERENCE));
                conferenceRoom.endConference();
                conferenceRoomService.save(conferenceRoom);
                return;
            }

            log.info("방장아님");


            if(redisUtils.isValueInSortedSet(roomId+"order:cur", exitUserNickname)){
                redisUtils.removeValueFromSortedSet(roomId+"order:cur", exitUserNickname);
            }


            // 대기방 상태 확인 및 메시지 발송
            if (conferenceRoom.getStep().equals(Step.WAIT)) {
                rabbitTemplate.convertAndSend("amq.topic", "room." + roomId, new WaitingRoomEnterExit(MessageType.EXIT_WAITING_ROOM));
            } else {
                redisUtils.setDataInSet(roomId+":out",exitUserNickname,7200L);
                String nextUser=messageService.NextOrder(roomId,exitUserNickname);
                List<String> usersInRoom = messageService.getUsersInRoom(roomId);
                messageService.updateCurOrder(roomId,nextUser);

                boolean nextUserIsFirst=false;
                if(messageService.isFirstOrder(roomId,nextUser)){
                    nextUserIsFirst=true;
                }
                if(messageService.isAi(roomId,nextUser)){
                    rabbitTemplate.convertAndSend("amq.topic", "room." + roomId, new ConferencesEnterExit(MessageType.EXIT_CONFERENCES, exitUserNickname, nextUser,true,nextUserIsFirst,usersInRoom));
                    return;
                }
                rabbitTemplate.convertAndSend("amq.topic", "room." + roomId, new ConferencesEnterExit(MessageType.EXIT_CONFERENCES, exitUserNickname, nextUser,false,nextUserIsFirst,usersInRoom));
            }


        } catch (Exception e) {
            // 예외 발생 시 로그 출력
            System.err.println("처리 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private String getNickName(Integer memberId,Integer roomId) {
        MemberHistoryId memberHistoryId=new MemberHistoryId(memberId,roomId);
        MemberHistory memberHistory= memberHistoryRepository.findById(memberHistoryId).get();
        return memberHistory.getNickName();
    }

    private Role getRole(Integer memberId,Integer roomId) {
        MemberHistoryId memberHistoryId=new MemberHistoryId(memberId,roomId);
        MemberHistory memberHistory= memberHistoryRepository.findById(memberHistoryId).get();
        return memberHistory.getRole();
    }

    private Integer getMemberId(String email) {
        return memberRepository.findByEmail(email).get().getId();
    }
}
