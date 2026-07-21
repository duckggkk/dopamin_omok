package com.dopamin.omok.game.physical.adapter.out.persistence;

import com.dopamin.omok.game.domain.StoneColor;
import com.dopamin.omok.game.physical.application.dto.PhysicalReplayData;
import com.dopamin.omok.game.physical.domain.PhysicalItemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 리플레이 JSON 컬럼의 하위 호환 검증.
 * 색상 필드를 String → StoneColor 로 바꿔도 기존에 쌓인 JSON("BLACK"/"WHITE"/null)이 그대로 읽혀야 한다.
 */
class PhysicalReplayConverterTest {

    private final PhysicalReplayConverter converter = new PhysicalReplayConverter();

    @Test
    @DisplayName("색상이 문자열로 저장된 기존 리플레이 JSON을 StoneColor 로 읽는다")
    void 기존_JSON_역직렬화() {
        String legacy = """
                {"gameId":7,"boardSize":14,"winCount":5,"durationMs":12345,
                 "winnerColor":"BLACK",
                 "players":[{"color":"BLACK","nickname":"흑돌","skin":null,"character":null},
                            {"color":"WHITE","nickname":"백돌","skin":null,"character":null}],
                 "events":[{"t":100,"x":3,"y":4,"v":1}],
                 "motionFrames":[{"t":100,"players":[{"color":"WHITE","x":1,"y":2,"heldItem":null,"speedBoosted":false}],
                                  "items":[{"x":5,"y":5,"type":"BOMB"}]}],
                 "trainingLog":null}
                """;

        PhysicalReplayData replay = converter.convertToEntityAttribute(legacy);

        assertThat(replay.winnerColor()).isEqualTo(StoneColor.BLACK);
        assertThat(replay.players()).extracting(PhysicalReplayData.PlayerInfo::color)
                .containsExactly(StoneColor.BLACK, StoneColor.WHITE);
        assertThat(replay.motionFrames().getFirst().players().getFirst().color()).isEqualTo(StoneColor.WHITE);
        assertThat(replay.motionFrames().getFirst().items().getFirst().type()).isEqualTo(PhysicalItemType.BOMB);
    }

    @Test
    @DisplayName("지금은 사라진 아이템 종류가 담긴 리플레이도 그 값만 null 로 비우고 열린다")
    void 삭제된_아이템_enum_역직렬화() {
        String withRemovedItem = """
                {"gameId":9,"boardSize":14,"winCount":5,"durationMs":300,"winnerColor":"WHITE",
                 "players":[],"events":[{"t":10,"x":1,"y":1,"v":2}],
                 "motionFrames":[{"t":10,
                     "players":[{"color":"BLACK","x":0,"y":0,"heldItem":"LEGACY_ITEM","speedBoosted":true}],
                     "items":[{"x":2,"y":2,"type":"LEGACY_ITEM"}]}],
                 "trainingLog":null}
                """;

        PhysicalReplayData replay = converter.convertToEntityAttribute(withRemovedItem);

        PhysicalReplayData.MotionFrame frame = replay.motionFrames().getFirst();
        assertThat(frame.players().getFirst().heldItem()).isNull();
        assertThat(frame.items().getFirst().type()).isNull();
        // 나머지 데이터는 멀쩡해야 한다 — 아이템 하나 때문에 판 전체를 잃지 않는다
        assertThat(replay.winnerColor()).isEqualTo(StoneColor.WHITE);
        assertThat(replay.events()).hasSize(1);
        assertThat(frame.players().getFirst().speedBoosted()).isTrue();
    }

    @Test
    @DisplayName("무효/중단 게임의 winnerColor null 을 그대로 읽는다")
    void 승자없는_JSON_역직렬화() {
        String aborted = """
                {"gameId":8,"boardSize":14,"winCount":5,"durationMs":500,"winnerColor":null,
                 "players":[],"events":[],"motionFrames":null,"trainingLog":null}
                """;

        assertThat(converter.convertToEntityAttribute(aborted).winnerColor()).isNull();
    }

    @Test
    @DisplayName("직렬화 결과는 예전과 동일한 문자열 형태를 유지한다(프론트 계약 불변)")
    void 직렬화_형태_유지() {
        PhysicalReplayData replay = new PhysicalReplayData(
                1L, 14, 5, 100L, StoneColor.WHITE,
                java.util.List.of(new PhysicalReplayData.PlayerInfo(StoneColor.BLACK, "흑돌", null, null)),
                java.util.List.of(), java.util.List.of(), null);

        String json = converter.convertToDatabaseColumn(replay);

        assertThat(json).contains("\"winnerColor\":\"WHITE\"").contains("\"color\":\"BLACK\"");
    }
}
