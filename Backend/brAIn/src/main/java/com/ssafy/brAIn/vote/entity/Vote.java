package com.ssafy.brAIn.vote.entity;

import com.ssafy.brAIn.conferenceroom.entity.ConferenceRoom;
import com.ssafy.brAIn.member.entity.Member;
import com.ssafy.brAIn.roundpostit.entity.RoundPostIt;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "room_id", referencedColumnName = "id")
    private ConferenceRoom conferenceRoom;

    @ManyToOne
    @JoinColumn(name = "postit_id", referencedColumnName = "id")
    private RoundPostIt roundPostIt;

    @Column(name = "score")
    private int score;

    @Column(name = "vote_type")
    @Enumerated(value = EnumType.STRING)
    private VoteType voteType;

    @Builder
    private Vote(int score, VoteType voteType, ConferenceRoom conferenceRoom, RoundPostIt roundPostIt, Member member) {
        this.score = score;
        this.voteType = voteType;

        this.conferenceRoom = conferenceRoom;
        this.roundPostIt = roundPostIt;
    }

    // 최종 투표 갱신
    public void updateScore(int score) {
        this.score = score;
    }

    // Vote Type 갱신
    public void updateVoteType(VoteType voteType) {
        this.voteType = voteType;
    }
}
