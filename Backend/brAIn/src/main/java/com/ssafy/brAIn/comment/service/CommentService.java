package com.ssafy.brAIn.comment.service;

import com.ssafy.brAIn.auth.jwt.JWTUtilForRoom;
import com.ssafy.brAIn.auth.jwt.JwtUtil;
import com.ssafy.brAIn.comment.entity.Comment;
import com.ssafy.brAIn.comment.repository.CommentRepository;
import com.ssafy.brAIn.exception.BadRequestException;
import com.ssafy.brAIn.member.entity.Member;
import com.ssafy.brAIn.member.repository.MemberRepository;
import com.ssafy.brAIn.roundpostit.entity.RoundPostIt;
import com.ssafy.brAIn.roundpostit.repository.RoundPostItRepository;
import com.ssafy.brAIn.roundpostit.service.RoundPostItService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class CommentService {

    private final MemberRepository memberRepository;
    private final RoundPostItRepository roundPostItRepository;
    private final CommentRepository commentRepository;
    private final RoundPostItService roundPostItService;
    private final JWTUtilForRoom jwtUtilForRoom;

    // 코멘트 생성
    @Transactional
    public Comment createComment(String token, Integer roundPostItId, String content) {
        String email = jwtUtilForRoom.getUsername(token);
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));

        RoundPostIt roundPostIt = roundPostItRepository.findById(roundPostItId)
                .orElseThrow(() -> new BadRequestException("RoundPostIt not found"));

        Comment comment = Comment.builder()
                .member(member)
                .roundPostIt(roundPostIt)
                .content(content)
                .build();

        commentRepository.save(comment);
        return comment;
    }

    // 코멘트 수정
    @Transactional
    public Comment updateComment(String token, Integer commentId, String updateContent) {
        String email = JwtUtil.getEmail(token);
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BadRequestException("Comment not found"));

        // 댓글 작성자와 수정하려는 유저와 일치한지 확인
        if (!comment.getMember().equals(member)) {
            throw new BadRequestException("Unauthorized action");
        }

        comment.updateContent(updateContent);
        commentRepository.save(comment);
        return comment;
    }

    // 코멘트 삭제
    @Transactional
    public void deleteComment(String token, Integer roundPostItId, Integer commentId) {
        String email = JwtUtil.getEmail(token);
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));

        RoundPostIt roundPostIt = roundPostItRepository.findById(roundPostItId)
                .orElseThrow(() -> new BadRequestException("RoundPostIt not found"));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BadRequestException("Comment not found"));

        // 댓글 작성자와 삭제하려는 유저와 일치한지 확인
        if (!comment.getMember().equals(member)) {
            throw new BadRequestException("Unauthorized action");
        }

        // 댓글이 해당 라운드 포스트잇에 속하는지 확인
        if (!comment.getRoundPostIt().equals(roundPostIt)) {
            throw new BadRequestException("Comment does not belong to the specified RoundPostIt");
        }

        commentRepository.delete(comment);
    }

    // Ready 상태 업데이트
    @Transactional
    public Map<String, Object> changeReady(String token, Integer roundPostItId) {
        String email = JwtUtil.getEmail(token);
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));

        RoundPostIt roundPostIt = roundPostItRepository.findById(roundPostItId)
                .orElseThrow(() -> new BadRequestException("RoundPostIt not found"));

        // 특정 RoundPostIt에서 해당 사용자가 작성한 모든 Comment를 가져옴
        List<Comment> userComments = commentRepository.findByRoundPostItAndMember(roundPostIt, member);

        // 모든 댓글의 ready 상태를 true로 설정
        for (Comment comment : userComments) {
            comment.changeReady(true);
        }

        commentRepository.saveAll(userComments);

        Map<String, Object> result = new HashMap<>();

        // 모든 참여자가 Ready 상태인지 확인
        if (allMembersReady(roundPostIt)) {
            // 다음 RoundPostIt 활성화
            RoundPostIt nextRoundPostIt = roundPostItService.getNextRoundPostIt();
            if (nextRoundPostIt != null) {
                // 다음 포스트잇 ID 반환
                result.put("nextRoundPostItId", nextRoundPostIt.getId());
                result.put("message", "Next RoundPostIt activated");
            } else {
                // 모든 포스트잇 구체화 종료
                result.put("message", "All RoundPostIts are completed");
            }
        } else {
            result.put("message", "Waiting for other participants to be ready");
        }

        return result;
    }

    // 모든 참여자가 Ready 상태인지 확인하는 메서드
    private boolean allMembersReady(RoundPostIt roundPostIt) {
        // 모든 댓글이 ready인지 확인
        return roundPostIt.getComments().stream().allMatch(Comment::isReady);
    }

    public List<Comment> findByPostItId(RoundPostIt roundPostIt) {
        return commentRepository.findByRoundPostIt(roundPostIt);
    }
}