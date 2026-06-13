package com.dopamin.omok.game.physical.adapter.out.persistence;

import com.dopamin.omok.game.physical.application.dto.PhysicalReplayData;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "physical_game_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhysicalGameRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false, unique = true)
    private Long gameId;

    @Convert(converter = PhysicalReplayConverter.class)
    @Column(name = "replay", columnDefinition = "json", nullable = false)
    private PhysicalReplayData replay;

    public PhysicalGameRecordEntity(Long gameId, PhysicalReplayData replay) {
        this.gameId = gameId;
        this.replay = replay;
    }
}
