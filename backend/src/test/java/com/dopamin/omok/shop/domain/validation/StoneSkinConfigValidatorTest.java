package com.dopamin.omok.shop.domain.validation;

import com.dopamin.omok.shop.domain.ItemConfig;
import com.dopamin.omok.shop.domain.ItemType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 바둑알 스킨(STONE_SKIN) / 착수 효과(STONE_EFFECT) config 검증 테스트.
 * 부팅 시 ItemConfigValidationRunner 가 "item_config(JSON) → ItemConfig 역직렬화 → 검증기 디스패치"
 * 순으로 도는데, 이 테스트가 동일 경로를 재현해 V14/V15 마이그레이션 데이터가 정확함을 DB 없이 보장한다.
 */
class StoneSkinConfigValidatorTest {

    // 부팅 시와 동일하게 등록된 검증기들을 타입별로 디스패치하는 레지스트리
    private final ItemConfigValidators validators = new ItemConfigValidators(List.of(
            new StoneSkinConfigValidator(),
            new StoneEffectConfigValidator(),
            new BoardSkinConfigValidator(),
            new StoneSoundConfigValidator()
    ));
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static ItemConfig stoneSkin(String name, ItemConfig.StoneStyle stone, String effect) {
        return new ItemConfig(name, null, null, null, stone, effect, null);
    }

    @Test
    @DisplayName("유효한 바둑알 스킨 config는 통과한다")
    void validStoneSkinPasses() {
        ItemConfig config = stoneSkin("금돌",
                new ItemConfig.StoneStyle("#d4af37", "#8a6d1b", "#fff3c4"), null);

        assertThatCode(() -> validators.validate(ItemType.STONE_SKIN, config))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("stone이 없으면 거부된다(미보유 우회용 빈 스킨 방지)")
    void missingStoneRejected() {
        ItemConfig config = stoneSkin("이름만", null, null);

        assertThatThrownBy(() -> validators.validate(ItemType.STONE_SKIN, config))
                .isInstanceOf(ItemConfigValidator.InvalidItemConfigException.class);
    }

    @Test
    @DisplayName("stone 색상이 유효한 hex가 아니면 거부된다")
    void invalidHexRejected() {
        ItemConfig config = stoneSkin("금돌",
                new ItemConfig.StoneStyle("gold", "#8a6d1b", "#fff3c4"), null);

        assertThatThrownBy(() -> validators.validate(ItemType.STONE_SKIN, config))
                .isInstanceOf(ItemConfigValidator.InvalidItemConfigException.class);
    }

    @Test
    @DisplayName("고급 스킨은 애니메이션(effect)을 번들할 수 있다 — 알려진 effect는 통과")
    void premiumStoneSkinWithEffectPasses() {
        ItemConfig config = stoneSkin("루비돌",
                new ItemConfig.StoneStyle("#c0314b", "#7a1226", "#ffd2dc"), "bounce");

        assertThatCode(() -> validators.validate(ItemType.STONE_SKIN, config))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("스킨에 지원하지 않는 effect가 붙으면 거부된다")
    void stoneSkinWithUnknownEffectRejected() {
        ItemConfig config = stoneSkin("이상한돌",
                new ItemConfig.StoneStyle("#d4af37", "#8a6d1b", "#fff3c4"), "explode");

        assertThatThrownBy(() -> validators.validate(ItemType.STONE_SKIN, config))
                .isInstanceOf(ItemConfigValidator.InvalidItemConfigException.class);
    }

    @Test
    @DisplayName("유효한 착수 효과(STONE_EFFECT) config는 통과하고, effect 없으면 거부된다")
    void stoneEffectValidation() {
        ItemConfig valid = new ItemConfig("뽀잉", null, null, null, null, "bounce", null);
        assertThatCode(() -> validators.validate(ItemType.STONE_EFFECT, valid))
                .doesNotThrowAnyException();

        ItemConfig noEffect = new ItemConfig("효과없음", null, null, null, null, null, null);
        assertThatThrownBy(() -> validators.validate(ItemType.STONE_EFFECT, noEffect))
                .isInstanceOf(ItemConfigValidator.InvalidItemConfigException.class);

        ItemConfig unknown = new ItemConfig("이상한효과", null, null, null, null, "explode", null);
        assertThatThrownBy(() -> validators.validate(ItemType.STONE_EFFECT, unknown))
                .isInstanceOf(ItemConfigValidator.InvalidItemConfigException.class);
    }

    @Test
    @DisplayName("V14/V15 마이그레이션의 실제 item_config JSON이 역직렬화되고 검증을 통과한다")
    void migrationConfigsDeserializeAndValidate() throws Exception {
        // V14: 금돌/옥돌 (색만), V15: 루비돌(색+effect 번들)
        String[] skinConfigs = {
                "{\"displayName\":\"금돌\",\"stone\":{\"fill\":\"#d4af37\",\"stroke\":\"#8a6d1b\",\"shine\":\"#fff3c4\"}}",
                "{\"displayName\":\"옥돌\",\"stone\":{\"fill\":\"#3fa66a\",\"stroke\":\"#1f5e3a\",\"shine\":\"#d6ffe6\"}}",
                "{\"displayName\":\"루비돌\",\"stone\":{\"fill\":\"#c0314b\",\"stroke\":\"#7a1226\",\"shine\":\"#ffd2dc\"},\"effect\":\"bounce\"}"
        };
        for (String json : skinConfigs) {
            ItemConfig config = objectMapper.readValue(json, ItemConfig.class);
            assertThat(config.stone()).isNotNull();
            assertThatCode(() -> validators.validate(ItemType.STONE_SKIN, config))
                    .doesNotThrowAnyException();
        }

        // V15: 착수 효과 아이템 "뽀잉"
        ItemConfig effectConfig = objectMapper.readValue(
                "{\"displayName\":\"뽀잉\",\"effect\":\"bounce\"}", ItemConfig.class);
        assertThat(effectConfig.effect()).isEqualTo("bounce");
        assertThatCode(() -> validators.validate(ItemType.STONE_EFFECT, effectConfig))
                .doesNotThrowAnyException();
    }
}
