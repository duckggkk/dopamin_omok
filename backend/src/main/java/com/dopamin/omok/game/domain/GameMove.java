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
@Table(name = "game_moves",
        indexes = {
                @Index(name = "idx_game_moves_game_id", columnList = "game_id")
        },
        // 애플리케이션 락(GameJpaRepository.findActiveGameByRoomCodeForUpdate)이 1차 방어선이고,
        // 아래 제약은 그것이 뚫렸을 때를 대비한 2차 방어선이다.
        // 이 테이블은 클래식 오목(GameService.placeStone)만 사용한다.
        // 피지컬 오목은 돌이 파괴·재배치되지만 별도 저장소(physical_game_records)를 쓰므로
        // 좌표 유니크 제약과 무관하다.
        uniqueConstraints = {
                // 한 게임에 같은 수순 번호가 두 번 저장되는 것을 막는다(동시 착수 → moveNumber 중복).
                @UniqueConstraint(name = "uk_game_moves_game_seq", columnNames = {"game_id", "move_number"}),
                // 같은 자리에 돌이 두 번 놓이는 것을 막는다(클래식은 돌을 제거하지 않는다).
                @UniqueConstraint(name = "uk_game_moves_game_pos", columnNames = {"game_id", "row_pos", "col"})
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

    /**
     * 착수자. 계정이 하드 삭제되면(게스트 정리 등) DB 가 SET NULL 로 비운다(V38) —
     * 기보는 상대방의 기록이기도 하므로 착수자가 사라져도 수는 남긴다.
     * 돌 색은 color 컬럼에 직접 저장돼 있어 착수자 없이도 재생에 문제없다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
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
