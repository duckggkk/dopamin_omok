package com.dopamin.omok.game.domain;

import com.dopamin.omok.user.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "games", indexes = {
        @Index(name = "idx_games_room_id", columnList = "room_id"),
        @Index(name = "idx_games_black_player", columnList = "black_player_id"),
        @Index(name = "idx_games_white_player", columnList = "white_player_id"),
        @Index(name = "idx_games_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private Integer gameNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "black_player_id")
    private User blackPlayer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "white_player_id")
    private User whitePlayer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private StoneColor currentTurn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime finishedAt;

    @Column
    private LocalDateTime lastMoveAt;

    @Builder
    private Game(Room room, Integer gameNumber, User blackPlayer, User whitePlayer) {
        this.room = room;
        this.gameNumber = gameNumber;
        this.blackPlayer = blackPlayer;
        this.whitePlayer = whitePlayer;
        this.status = GameStatus.IN_PROGRESS;
        this.currentTurn = StoneColor.BLACK;
        this.startedAt = LocalDateTime.now();
        this.lastMoveAt = LocalDateTime.now();
    }

    public static Game start(Room room, User blackPlayer, User whitePlayer) {
        return Game.builder()
                .room(room)
                .gameNumber(room.getCurrentGameNumber())
                .blackPlayer(blackPlayer)
                .whitePlayer(whitePlayer)
                .build();
    }

    public String getRoomCode() {
        return room.getRoomCode();
    }

    public void switchTurn() {
        this.currentTurn = (this.currentTurn == StoneColor.BLACK)
                ? StoneColor.WHITE
                : StoneColor.BLACK;
        this.lastMoveAt = LocalDateTime.now();
    }

    public void recordMove() {
        this.lastMoveAt = LocalDateTime.now();
    }

    public void finish(User winner) {
        this.winner = winner;
        this.status = GameStatus.FINISHED;
        this.finishedAt = LocalDateTime.now();
    }

    public void draw() {
        this.status = GameStatus.DRAW;
        this.finishedAt = LocalDateTime.now();
    }

    public void abandon() {
        this.status = GameStatus.ABANDONED;
        this.finishedAt = LocalDateTime.now();
    }

    public boolean isParticipant(Long userId) {
        return (blackPlayer != null && blackPlayer.getId().equals(userId)) ||
               (whitePlayer != null && whitePlayer.getId().equals(userId));
    }

    public boolean isInProgress() {
        return this.status == GameStatus.IN_PROGRESS;
    }

    public StoneColor getPlayerColor(Long userId) {
        if (blackPlayer != null && blackPlayer.getId().equals(userId)) return StoneColor.BLACK;
        if (whitePlayer != null && whitePlayer.getId().equals(userId)) return StoneColor.WHITE;
        return null;
    }

    public User getOpponent(Long userId) {
        if (blackPlayer != null && blackPlayer.getId().equals(userId)) return whitePlayer;
        if (whitePlayer != null && whitePlayer.getId().equals(userId)) return blackPlayer;
        return null;
    }

    /**
     * 레이팅·전적에 반영할 대국인지. 회원 대 회원 대국만 반영한다.
     *
     * 방의 '랭크/캐주얼' 표시는 판단에 쓰지 않는다 — 그 값은 방 목록에 붙는 라벨일 뿐이고,
     * 반영 여부는 누가 뒀는지로만 정한다. 봇(AI 연습)과 게스트는 레이팅을 오염시키므로 제외한다.
     */
    public boolean isRated() {
        return isRatedPlayer(blackPlayer) && isRatedPlayer(whitePlayer);
    }

    private static boolean isRatedPlayer(User user) {
        return user != null && !user.isBot() && !user.isGuest();
    }
}
