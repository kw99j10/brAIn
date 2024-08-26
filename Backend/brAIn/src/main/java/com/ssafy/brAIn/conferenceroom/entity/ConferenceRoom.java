package com.ssafy.brAIn.conferenceroom.entity;

import com.ssafy.brAIn.history.entity.MemberHistory;
import com.ssafy.brAIn.util.CommonUtils;
import com.ssafy.brAIn.util.MeetingUrlGenerator;
import jakarta.persistence.*;

import lombok.*;

import java.util.Date;
import java.util.List;

@Entity // 엔티티로 지정
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConferenceRoom {
    @Id // id 필드를 기본키로 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Integer id;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "step", nullable = false)
    private Step step;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "timer")
    private Date timer;

    @Column(name = "is_end")
    private Boolean isEnd;

    @Setter
    @Column(name = "conclusion", columnDefinition = "TEXT")
    private String conclusion;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "start_time")
    private Date startTime;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "end_time")
    private Date endTime;

    @Column(name = "invite_code")
    private String inviteCode;

    @Column(name = "thread_id")
    private String threadId;

    @Column(name = "assistant_id")
    private String assistantId;

    @Column(name = "secure_id")
    private String secureId;

    @Column(name = "participate_url")
    private String participateUrl;


    // 히스토리와 양방향 관계 설정
    @OneToMany(mappedBy = "conferenceRoom", fetch = FetchType.LAZY)
    private List<MemberHistory> memberHistories;
    
    @Column(name = "round")
    private Integer round;

    @Builder
    public ConferenceRoom(String subject){
        this.subject = subject;
        this.step = Step.WAIT;
        this.isEnd = false;
        this.startTime = new Date();
        this.inviteCode = CommonUtils.generateRandomMixStr(6,true);
        this.secureId = MeetingUrlGenerator.generateMeetingUrl();
        this.participateUrl = String.format("https://bardisue.store/v1/conferences/%s", this.secureId);
        this.round = 0;
    }

    public void updateConferenceRoom(String subject, Date startTime) {
        this.subject = subject;
        this.startTime = startTime;
    }

    public ConferenceRoom updateStep(Step step) {
        this.step = step;
        return this;
    }

    public ConferenceRoom updateAi(String AssistantId, String ThreadId) {
        this.assistantId = AssistantId;
        this.threadId = ThreadId;
        return this;
    }

    // 타이머 설정
    public void setTimer(int minutes) {
        long timerInMillis = minutes * 60 * 1000;
        this.timer = new Date(System.currentTimeMillis() + timerInMillis);
    }


    // 타이머 종료 확인
    public boolean isTimerExpired() {
        return timer != null && new Date().after(timer);
    }


    //회의 정보 업데이트 -> 회의록의 내용과 종료 시간
    public void updateConferenceDetails(String conclusion, Date endTime) {
        this.conclusion = conclusion;
        this.endTime = endTime;
    }

    public void endConference() {
        this.isEnd = true;
    }
}

// 빌더 패턴을 사용하면 객체를 유연하고 직관적으로 생성할 수 있음, 어느 필드에 어떤 값이 들어가는지 명시적으로 파악할 수 있음.