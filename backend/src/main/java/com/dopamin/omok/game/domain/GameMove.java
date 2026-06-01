package com.dopamin.omok.game.domain;

import com.dopamin.omok.user.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_moves", indexes = {
        @Index(name = "idx_game_moves_game_id", columnList = "game_id"),
        @Index(name = "idx_game_moves_game_seq", columnList = "game_id, move_number")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameMove {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private User player;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StoneColor color;

    @Column(name = "row_pos", nullable = false)
    private Integer row;

    @Column(nullable = false)
    private Integer col;

    @Column(nullable = false)
    private Integer moveNumber;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private GameMove(Game game, User player, StoneColor color,
                     Integer row, Integer col, Integer moveNumber) {
        this.game = game;
        this.player = player;
        this.color = color;
        this.row = row;
        this.col = col;
        this.moveNumber = moveNumber;
    }

    public static GameMove of(Game game, User player, StoneColor color,
                              int row, int col, int moveNumber) {
        return GameMove.builder()
                .game(game)
                .player(player)
                .color(color)
                .row(row)
                .col(col)
                .moveNumber(moveNumber)
                .build();
    }
}
