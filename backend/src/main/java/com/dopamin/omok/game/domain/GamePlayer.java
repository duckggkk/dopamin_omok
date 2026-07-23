package com.dopamin.omok.game.domain;

import com.dopamin.omok.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_players", indexes = {
        @Index(name = "idx_game_players_room", columnList = "room_id"),
        @Index(name = "idx_game_players_user", columnList = "user_id")
},
        uniqueConstraints = @UniqueConstraint(
                name = "uk_game_players_room_user",
                columnNames = {"room_id", "user_id"}
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GamePlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlayerRole role;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private StoneColor color;

    @Column
    private Integer remainingSeconds;

    @Column(nullable = false)
    private boolean inByoyomi = false;

    @Column(name = "is_ready", nullable = false)
    private boolean ready = false;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    @Builder
    private GamePlayer(Room room, User user, PlayerRole role,
                       StoneColor color, Integer remainingSeconds) {
        this.room = room;
        this.user = user;
        this.role = role;
        this.color = color;
        this.remainingSeconds = remainingSeconds;
        this.inByoyomi = false;
        this.joinedAt = LocalDateTime.now();
    }

    public static GamePlayer createHost(Room room, User user) {
        return GamePlayer.builder()
                .room(room)
                .user(user)
                .role(PlayerRole.HOST)
                .color(StoneColor.BLACK)
                .remainingSeconds(room.getTimeLimit().getSeconds())
                .build();
    }

    /** 참가자 색은 호출부가 정한다 — 방장이 흑백을 바꿨을 수 있어 백 고정 배정이 불가능하다. */
    public static GamePlayer createPlayer(Room room, User user, StoneColor color) {
        return GamePlayer.builder()
                .room(room)
                .user(user)
                .role(PlayerRole.PLAYER)
                .color(color)
                .remainingSeconds(room.getTimeLimit().getSeconds())
                .build();
    }

    public static GamePlayer createSpectator(Room room, User user) {
        return GamePlayer.builder()
                .room(room)
                .user(user)
                .role(PlayerRole.SPECTATOR)
                .color(null)
                .remainingSeconds(null)
                .build();
    }

    public boolean isSpectator() {
        return this.role == PlayerRole.SPECTATOR;
    }

    public boolean isHost() {
        return this.role == PlayerRole.HOST;
    }

    public boolean isPlayer() {
        return this.role == PlayerRole.PLAYER;
    }

    public boolean isParticipant() {
        return this.role == PlayerRole.HOST || this.role == PlayerRole.PLAYER;
    }

    /**
     * 경과 시간을 차감하고, 시간 초과 여부를 반환한다.
     * 메인 시간이 남아 있으면 차감, 소진 시 초읽기로 전환.
     * 초읽기 시간도 초과하면 true(패배) 반환.
     */
    public boolean deductTimeAndCheckTimeout(long elapsedSeconds, ByoyomiOption byoyomiOption) {
        if (remainingSeconds == null) {
            return false;
        }
        if (!inByoyomi) {
            long newRemaining = remainingSeconds - elapsedSeconds;
            if (newRemaining > 0) {
                this.remainingSeconds = (int) newRemaining;
                return false;
            }
            if (!byoyomiOption.hasByoyomi()) {
                return true;
            }
            // 메인 시간 소진 → 초읽기 시작
            this.inByoyomi = true;
            long byoyomiLeft = byoyomiOption.getSeconds() + newRemaining; // 초읽기 시간에서 초과분 차감
            if (byoyomiLeft <= 0) return true;
            this.remainingSeconds = (int) byoyomiLeft;
            return false;
        }
        // 초읽기 중: 매 수마다 초읽기 시간으로 리셋하므로 초과 시 패배
        if (elapsedSeconds > byoyomiOption.getSeconds()) {
            return true;
        }
        // 초읽기는 매 수마다 리셋
        this.remainingSeconds = byoyomiOption.getSeconds();
        return false;
    }

    public void toggleReady() {
        this.ready = !this.ready;
    }

    /** 항상 준비 상태로 둔다(봇 — 방장이 '게임 시작'만 누르면 되도록). */
    public void markReady() {
        this.ready = true;
    }

    public void resetReady() {
        this.ready = false;
    }

    public void resetTimeForRematch(Integer seconds) {
        this.remainingSeconds = seconds;
        this.inByoyomi = false;
        this.ready = false;
    }

    public void swapColor() {
        this.color = (this.color == StoneColor.BLACK) ? StoneColor.WHITE : StoneColor.BLACK;
    }
}
