package com.ssafy.brAIn.comment.controller;

import com.ssafy.brAIn.auth.jwt.JWTUtilForRoom;
import com.ssafy.brAIn.comment.dto.CommentCreateRequest;
import com.ssafy.brAIn.comment.dto.CommentRequest;
import com.ssafy.brAIn.comment.dto.RoundPostItContent;
import com.ssafy.brAIn.comment.entity.Comment;
import com.ssafy.brAIn.comment.service.CommentService;
import com.ssafy.brAIn.roundpostit.entity.RoundPostIt;
import com.ssafy.brAIn.roundpostit.service.RoundPostItService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/comment")
public class CommentController {

    private final CommentService commentService;
    private final RoundPostItService roundPostItService;
    private final JWTUtilForRoom jwtUtilForRoom;

    // 코멘트 생성
    @PostMapping("/create")
    public ResponseEntity<?> createComment(@RequestHeader("AuthorizationRoom") String token,
                                           @RequestBody CommentCreateRequest commentCreateRequest) {
        String roomToken = token.replace("Bearer ", "");
        String target = commentCreateRequest.getTarget();
        String content = commentCreateRequest.getComment();
        System.out.println(target);
        Integer roundPostItId = roundPostItService.findByContentAndRoom(target,jwtUtilForRoom.getRoomId(roomToken)).get().getId();
        System.out.println("roundPostItId: " + roundPostItId);


        System.out.println("comment달기:"+content);
        Comment comment = commentService.createComment(roomToken, roundPostItId, content);
        return ResponseEntity.ok(Map.of("Comment", comment, "message", "Comment created successfully"));
    }

    // 코멘트 수정
    @PutMapping("/update")
    public ResponseEntity<?> updateComment(@RequestHeader("AuthorizationRoom") String token,
                                           @RequestBody CommentCreateRequest commentCreateRequest) {
        String roomToken = token.replace("Bearer ", "");
        String content = commentCreateRequest.getComment();

        Integer roundPostItId = roundPostItService.findByContentAndRoom(content,jwtUtilForRoom.getRoomId(roomToken)).get().getId();
        String updateContent = commentCreateRequest.getComment();

        Comment comment = commentService.updateComment(roomToken, roundPostItId, updateContent);
        return ResponseEntity.ok(Map.of("UpdatedComment", comment, "message", "Comment created successfully"));
    }

    // 코멘트 삭제
    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteComment(@RequestHeader("AuthorizationRoom") String token,
                                           @RequestBody CommentRequest commentRequest) {
        String accessToken = token.replace("Bearer ", "");
        Integer roundPostItId = commentRequest.getRoundPostItId();
        Integer commentId = commentRequest.getCommentId();

        commentService.deleteComment(accessToken, roundPostItId, commentId);
        return ResponseEntity.ok(Map.of("message", "Comment deleted successfully"));
    }

    // Ready 상태 설정
    @PutMapping("/{roundPostItId}/ready")
    public ResponseEntity<?> changeReady(@RequestHeader("AuthorizationRoom") String token,
                                         @PathVariable Integer roundPostItId) {
        String accessToken = token.replace("Bearer ", "");

        Map<String, Object> result = commentService.changeReady(accessToken, roundPostItId);
        String message = result.get("message").toString();

        if (result.containsKey("nextRoundPostItId")) {
            Integer nextRoundPostItId = (Integer) result.get("nextRoundPostItId");
            return ResponseEntity.ok(Map.of("nextRoundPostItId", nextRoundPostItId, "message", message));
        } else {
            return ResponseEntity.ok(Map.of("message", message));
        }
    }

    @PostMapping
    public ResponseEntity<?> getComment(@RequestBody RoundPostItContent roundPostItContent, @RequestHeader("AuthorizationRoom") String token) {
        String roomToken = token.replace("Bearer ", "");
        String roomId=jwtUtilForRoom.getRoomId(roomToken);
        log.info("roundpostitContent: {}",roundPostItContent.getContent());
        log.info("roomId:{}",roomId);
        RoundPostIt roundPostIt=roundPostItService.findByContentAndRoom(roundPostItContent.getContent(),roomId).get();

        List<Comment> commentList=commentService.findByPostItId(roundPostIt);
        List<String> comments=new ArrayList<>();
        for(Comment comment:commentList){
            comments.add(comment.getContent());
        }


        return ResponseEntity.ok(Map.of("comments",comments));
    }
}
