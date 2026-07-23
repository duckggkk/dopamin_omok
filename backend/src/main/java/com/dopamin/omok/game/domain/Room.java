package com.dopamin.omok.game.domain;

import com.dopamin.omok.user.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "rooms", indexes = {
        @Index(name = "idx_rooms_room_code", columnList = "room_code"),
        @Index(name = "idx_rooms_status", columnList = "status"),
        @Index(name = "idx_rooms_host", columnList = "host_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String roomCode;

    /**
     * 방장. 계정이 하드 삭제되면(게스트 정리 등) DB 가 SET NULL 로 비운다(V38) —
     * 방·대국 기록은 상대방의 것이기도 하므로 방장이 사라져도 남긴다.
     * 살아 있는(WAITING/IN_PROGRESS) 방의 방장은 항상 존재한다
     * (게스트 정리가 활성 방 참가자를 건너뛰기 때문). null 은 닫힌 방에서만 가능하다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User host;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameType gameType;

    /** 오목 규칙 변형(자유룰/렌주룰). 피지컬 오목에서는 의미 없으며 항상 FREESTYLE 로 저장한다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OmokRule omokRule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TimeLimit timeLimit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ByoyomiOption byoyomiOption;

    /**
     * 랭크전 여부. true=랭크(회원 전용, 레이팅·전적 반영), false=캐주얼(비회원도 참가, 미반영).
     * 기존 데이터 호환을 위해 기본 TRUE.
     */
    @Column(nullable = false)
    private boolean ranked = true;

    @Column(nullable = false)
    private Integer maxSpectators = 3;

    @Column(nullable = false)
    private Integer currentGameNumber = 0;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Room(User host, String roomCode, GameType gameType, OmokRule omokRule,
                 TimeLimit timeLimit, ByoyomiOption byoyomiOption, boolean ranked) {
        this.host = host;
        this.roomCode = roomCode;
        this.gameType = gameType;
        // 피지컬 오목은 렌주룰 개념이 없으므로 항상 FREESTYLE. 그 외엔 지정값(null이면 FREESTYLE).
        this.omokRule = (gameType == GameType.PHYSICAL || omokRule == null) ? OmokRule.FREESTYLE : omokRule;
        this.timeLimit = timeLimit;
        this.byoyomiOption = byoyomiOption;
        this.ranked = ranked;
        this.status = RoomStatus.WAITING;
        this.maxSpectators = 3;
        this.currentGameNumber = 0;
    }

    public static Room create(User host, String roomCode, GameType gameType, OmokRule omokRule,
                              TimeLimit timeLimit, ByoyomiOption byoyomiOption, boolean ranked) {
        return Room.builder()
                .host(host)
                .roomCode(roomCode)
                .gameType(gameType)
                .omokRule(omokRule)
                .timeLimit(timeLimit)
                .byoyomiOption(byoyomiOption)
                .ranked(ranked)
                .build();
    }

    /** 렌주룰(흑 금수 적용) 방인지. 피지컬은 항상 false. */
    public boolean isRenju() {
        return gameType == GameType.CLASSIC && omokRule == OmokRule.RENJU;
    }

    public void startGame() {
        this.status = RoomStatus.IN_PROGRESS;
        this.currentGameNumber++;
    }

    public void waitForNextGame() {
        this.status = RoomStatus.WAITING;
    }

    public void close() {
        this.status = RoomStatus.CLOSED;
    }

    public boolean isWaiting() {
        return this.status == RoomStatus.WAITING;
    }

    public boolean isInProgress() {
        return this.status == RoomStatus.IN_PROGRESS;
    }

    public boolean isClosed() {
        return this.status == RoomStatus.CLOSED;
    }

    public boolean isHost(Long userId) {
        return host != null && host.getId().equals(userId);
    }
}
