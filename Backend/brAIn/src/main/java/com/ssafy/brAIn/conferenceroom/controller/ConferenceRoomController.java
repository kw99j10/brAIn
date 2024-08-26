package com.ssafy.brAIn.conferenceroom.controller;

import com.ssafy.brAIn.auth.jwt.JWTUtilForRoom;
import com.ssafy.brAIn.auth.jwt.JwtUtil;
import com.ssafy.brAIn.conferenceroom.dto.*;
import com.ssafy.brAIn.conferenceroom.entity.ConferenceRoom;
import com.ssafy.brAIn.conferenceroom.service.ConferenceRoomService;
import com.ssafy.brAIn.conferenceroom.service.PdfService;
import com.ssafy.brAIn.history.model.Status;
import com.ssafy.brAIn.history.service.MemberHistoryService;
import com.ssafy.brAIn.util.RandomNicknameGenerator;
import com.ssafy.brAIn.util.RedisUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/conferences")
public class ConferenceRoomController {

    private final ConferenceRoomService conferenceRoomService;
    private final MemberHistoryService memberHistoryService;

    @Autowired
    private JWTUtilForRoom jwtUtilForRoom;
    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private PdfService pdfService;

    @GetMapping("/{roomId}")
    public ResponseEntity<?> getConferenceRoom(@PathVariable String roomId) {
        return ResponseEntity.status(200).body(roomId);
    }

    @PostMapping()
    public ResponseEntity<?> addConferenceRoom(@RequestBody ConferenceRoomRequest conferenceRoomRequest, @RequestHeader("Authorization") String token) {
        ConferenceRoom cr = conferenceRoomRequest.toConferenceRoom();
        ConferenceRoom saveCr = conferenceRoomService.save(cr);

        redisUtils.save(saveCr.getId() + ":time:init", conferenceRoomRequest.getTime() + "");

        token = token.split(" ")[1];
        System.out.println(token);
        Claims claims = JwtUtil.extractToken(token);
        String email = claims.get("email").toString();
        System.out.println(email);

        String randomNick = RandomNicknameGenerator.generateNickname();

        String jwtTokenForRoom = jwtUtilForRoom.createJwt("access", email, "CHIEF", randomNick, saveCr.getId() + "", 100000000L);
        System.out.println(jwtTokenForRoom);
        ConferenceRoomResponse crr = new ConferenceRoomResponse(cr, jwtTokenForRoom, randomNick);
        System.out.println(crr.toString());
        return ResponseEntity.status(201).body(crr);
    }

    @PostMapping("/join")
    public ResponseEntity<?> getConferenceRoom(@RequestBody ConferenceRoomJoinRequest conferenceRoomJoinRequest) {
        ConferenceRoom findConference = conferenceRoomService.findByInviteCode(conferenceRoomJoinRequest.getInviteCode());

        ConferenceRoomResponse crr = new ConferenceRoomResponse(findConference, "", "");
        return ResponseEntity.status(200).body(crr);
    }

    @GetMapping()
    public ResponseEntity<?> getConferenceRoomsInfo(@RequestParam("secureId") String secureId) {
        ConferenceRoom conferenceRoom = conferenceRoomService.findBySecureId(secureId);
        ConferenceRoomResponse crr = new ConferenceRoomResponse(conferenceRoom, "", "");
        return ResponseEntity.status(200).body(crr);
    }

    @PostMapping("/{url}")
    public ResponseEntity<?> joinConferenceRoom(@PathVariable String url, @RequestHeader("Authorization") String token) {
        ConferenceRoom findConference = conferenceRoomService.findBySecureId(url);

        token = token.split(" ")[1];
        Claims claims = JwtUtil.extractToken(token);
        String email = claims.get("email").toString();
        System.out.println("방은 들어감");
        String randomNick = RandomNicknameGenerator.generateNickname();

        String jwtTokenForRoom = jwtUtilForRoom.createJwt("access", email, "MEMBER", randomNick, findConference.getId() + "", 100000000L);

        ConferenceRoomResponse crr = new ConferenceRoomResponse(findConference, jwtTokenForRoom, randomNick);
        return ResponseEntity.status(200).body(crr);
    }


    // 새로운 회의 기록 생성 and 중간 회의록
    @PostMapping("/save")
    public ResponseEntity<?> createHistory(@RequestBody ConferenceMemberRequest conferenceSaveRequest) {
        try {
            ConferenceMemberRequest saveRequestDTO = conferenceRoomService.saveConferenceHistory(conferenceSaveRequest);
            return ResponseEntity.ok(saveRequestDTO);
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body("Conference Room not found");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("An error occurred while saving conference history");
        }
    }

    // 회의룸 재설정
    @PutMapping("/updateConferenceRoom")
    public ResponseEntity<?> updateConferenceRoom(@RequestHeader("Authorization") String token,
                                                  @RequestBody ConferenceUpdateRequest request) {
        try {
            // Barer 접두사 제거
            String roomToken = token.replace("Bearer ", "");
            // 회의룸 재설정
            String roomId = JwtUtil.getConferenceRoomId(roomToken);
            String subject = request.getSubject();
            Date startTime = request.getStartTime();

            conferenceRoomService.updateConferenceRoom(Integer.parseInt(roomId), subject, startTime);
            return ResponseEntity.ok(Map.of("message", "ConferenceRoom update successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Conference Room not found");
        }
    }

    @GetMapping("/time")
    public ResponseEntity<?> getConferenceRoomTime(@RequestParam("secureId") String secureId) {
        try {
            ConferenceRoom conferenceRoom = conferenceRoomService.findBySecureId(secureId);

            Integer roomId = conferenceRoom.getId();

            String time = redisUtils.getData(roomId + ":time:init");

            return ResponseEntity.ok(Map.of("time", Integer.parseInt(time) * 60 * 1000));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("No time");
        }
    }

    @GetMapping("/countUser/{roomId}")
    public ResponseEntity<?> countUser(@RequestHeader("Authorization") String token, @PathVariable String roomId) {
        int come = memberHistoryService.getHistoryByRoomId(Integer.parseInt(roomId)).stream()
                .filter((memberHistory -> memberHistory.getStatus().equals(Status.COME)))
                .toList().size();

        return ResponseEntity.ok(come);
    }

    // 회의 최종 결과물 보여주기
    @GetMapping("/products/{roomId}")
    public ResponseEntity<String> generateMeetingReport(@PathVariable Integer roomId) {
        try {
            String report = conferenceRoomService.generateMeetingReport(roomId);
            return ResponseEntity.ok(report);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Invalid room ID: " + roomId);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to generate report: " + e.getMessage());
        }
    }

    // PDF 파일 다운로드
    @GetMapping("/download/{roomId}")
    public void downloadPdf(@PathVariable Integer roomId, HttpServletResponse response) {
        try {
            // PDF 생성
            byte[] pdfBytes = pdfService.generatePdf(roomId);

            // PDF 파일 응답 설정
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "att" +
                    "" +
                    "achment; filename=meeting_report_" + roomId + ".pdf");
            response.getOutputStream().write(pdfBytes);
            response.flushBuffer();

        } catch (IllegalArgumentException e) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
        } catch (Exception e) {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    @GetMapping("/load/{roomId}")
    public ResponseEntity<String> loadProduct(@PathVariable Integer roomId, HttpServletResponse response) {
        try {
            // PDF 생성
            String result = conferenceRoomService.generateMeetingReport(roomId);

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Invalid room ID: " + roomId);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to generate report: " + e.getMessage());
        }
    }

}
