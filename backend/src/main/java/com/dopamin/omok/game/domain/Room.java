package com.dopamin.omok.game.domain;

import com.dopamin.omok.user.domain.User;
import jakarta.persistence.*;
import lombok.*;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameType gameType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TimeLimit timeLimit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ByoyomiOption byoyomiOption;

    @Column(nullable = false)
    private Integer maxSpectators = 3;

    @Column(nullable = false)
    private Integer currentGameNumber = 0;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Room(User host, String roomCode, GameType gameType,
                 TimeLimit timeLimit, ByoyomiOption byoyomiOption) {
        this.host = host;
        this.roomCode = roomCode;
        this.gameType = gameType;
        this.timeLimit = timeLimit;
        this.byoyomiOption = byoyomiOption;
        this.status = RoomStatus.WAITING;
        this.maxSpectators = 3;
        this.currentGameNumber = 0;
    }

    public static Room create(User host, String roomCode, GameType gameType,
                              TimeLimit timeLimit, ByoyomiOption byoyomiOption) {
        return Room.builder()
                .host(host)
                .roomCode(roomCode)
                .gameType(gameType)
                .timeLimit(timeLimit)
                .byoyomiOption(byoyomiOption)
                .build();
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
