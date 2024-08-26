package com.ssafy.brAIn.roundpostit.entity;

import com.ssafy.brAIn.vote.entity.Vote;
import com.ssafy.brAIn.comment.entity.Comment;
import com.ssafy.brAIn.conferenceroom.entity.ConferenceRoom;
import com.ssafy.brAIn.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

import java.util.Set;


@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoundPostIt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "room_id", referencedColumnName = "id")
    private ConferenceRoom conferenceRoom;

    @ManyToOne
    @JoinColumn(name = "member_id", referencedColumnName = "id")
    private Member member;

    @Column(name = "content")
    private String content;

    @Setter
    @Column(name = "persona", columnDefinition = "TEXT")
    private String persona;

    @Setter
    @Column(name = "swot", columnDefinition = "TEXT")
    private String swot;

    // 댓글 리스트
    @OneToMany(mappedBy = "roundPostIt", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Comment> comments;

    private boolean last9;

    private boolean last3;

    private boolean isAI;

    @OneToMany(mappedBy = "roundPostIt", cascade = CascadeType.ALL)
    private List<Vote> votes;

    @Builder
    public RoundPostIt(ConferenceRoom conferenceRoom, String content) {
        this.conferenceRoom = conferenceRoom;

        this.content = content;
    }

    @Builder
    public RoundPostIt(String persona) {
        this.persona = persona;
    }

    @Builder
    public RoundPostIt(ConferenceRoom conferenceRoom,String content, String persona) {
        this.conferenceRoom = conferenceRoom;
        this.content = content;
        this.persona = persona;
    }

    @Builder
    public RoundPostIt(ConferenceRoom conferenceRoom, boolean isAI, String content) {
        this.conferenceRoom = conferenceRoom;
        this.isAI = isAI;
        this.content = content;
    }

    public void selectedNine() {
        last9 = true;
    }

    public void selectedThree() {
        last3 = true;
    }
}
