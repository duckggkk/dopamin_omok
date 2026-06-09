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
 * 피지컬 캐릭터 스킨(CHARACTER_SKIN) config 검증 테스트.
 * 부팅 시 ItemConfigValidationRunner 와 동일 경로("item_config JSON → 역직렬화 → 검증기")를 재현해
 * V18 마이그레이션 데이터가 정확함을 DB 없이 보장한다.
 */
class CharacterSkinConfigValidatorTest {

    private final ItemConfigValidators validators =
            new ItemConfigValidators(List.of(new CharacterSkinConfigValidator()));
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static ItemConfig character(String name, ItemConfig.CharacterStyle character) {
        return new ItemConfig(name, null, null, null, null, null, character);
    }

    @Test
    @DisplayName("유효한 캐릭터 스킨 config는 통과한다")
    void validCharacterPasses() {
        ItemConfig config = character("로봇", new ItemConfig.CharacterStyle("#7fb3d5", "#2c3e50", "robot"));
        assertThatCode(() -> validators.validate(ItemType.CHARACTER_SKIN, config)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("character가 없으면 거부된다(미보유 우회용 빈 스킨 방지)")
    void missingCharacterRejected() {
        ItemConfig config = character("이름만", null);
        assertThatThrownBy(() -> validators.validate(ItemType.CHARACTER_SKIN, config))
                .isInstanceOf(ItemConfigValidator.InvalidItemConfigException.class);
    }

    @Test
    @DisplayName("body/accent가 유효한 hex가 아니면 거부된다")
    void invalidHexRejected() {
        ItemConfig config = character("로봇", new ItemConfig.CharacterStyle("blue", "#2c3e50", "robot"));
        assertThatThrownBy(() -> validators.validate(ItemType.CHARACTER_SKIN, config))
                .isInstanceOf(ItemConfigValidator.InvalidItemConfigException.class);
    }

    @Test
    @DisplayName("face가 안전 키워드가 아니면 거부된다(이모지/경로 주입 방지)")
    void invalidFaceRejected() {
        ItemConfig config = character("로봇", new ItemConfig.CharacterStyle("#7fb3d5", "#2c3e50", "🤖"));
        assertThatThrownBy(() -> validators.validate(ItemType.CHARACTER_SKIN, config))
                .isInstanceOf(ItemConfigValidator.InvalidItemConfigException.class);
    }

    @Test
    @DisplayName("V18 마이그레이션의 실제 item_config JSON이 역직렬화되고 검증을 통과한다")
    void migrationConfigsDeserializeAndValidate() throws Exception {
        String[] configs = {
                "{\"displayName\":\"로봇\",\"character\":{\"body\":\"#7fb3d5\",\"accent\":\"#2c3e50\",\"face\":\"robot\"}}",
                "{\"displayName\":\"토끼\",\"character\":{\"body\":\"#f7d6e0\",\"accent\":\"#c97b9a\",\"face\":\"rabbit\"}}",
                "{\"displayName\":\"유령\",\"character\":{\"body\":\"#e3e8ef\",\"accent\":\"#8895a7\",\"face\":\"ghost\"}}"
        };
        for (String json : configs) {
            ItemConfig config = objectMapper.readValue(json, ItemConfig.class);
            assertThat(config.character()).isNotNull();
            assertThatCode(() -> validators.validate(ItemType.CHARACTER_SKIN, config)).doesNotThrowAnyException();
        }
    }
}
