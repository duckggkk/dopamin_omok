package com.dopamin.omok.shop.domain.validation;

import com.dopamin.omok.shop.domain.ItemConfig;

import java.util.Set;
import java.util.regex.Pattern;

import static com.dopamin.omok.shop.domain.validation.ItemConfigValidator.InvalidItemConfigException;

/**
 * 검증 전략들이 공유하는 공통 체크 유틸.
 */
final class ConfigChecks {

    private static final Pattern HEX_COLOR = Pattern.compile("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");
    // assetKey는 파일 경로 일부로 쓰이므로 경로 조작 방지를 위해 안전 문자만 허용
    private static final Pattern SAFE_ASSET_KEY = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,49}$");
    // 캐릭터 face는 프론트 이모지 매핑 키워드 — 안전한 소문자 키만 허용
    private static final Pattern FACE_KEY = Pattern.compile("^[a-z][a-z0-9_]{0,19}$");
    private static final Set<String> VALID_FILTER_TYPES = Set.of("fractalNoise", "turbulence");
    private static final Set<String> VALID_BLEND_MODES =
            Set.of("normal", "multiply", "screen", "overlay", "darken", "lighten", "soft-light", "hard-light");
    // 지원하는 착수 효과 키 (프론트가 렌더할 수 있는 애니메이션). 화이트리스트로 임의 값 차단.
    private static final Set<String> VALID_EFFECTS = Set.of("bounce");
    // 지원하는 패배 이펙트 키 (패자 화면에서 프론트가 렌더할 연출). 화이트리스트로 임의 값 차단.
    private static final Set<String> VALID_DEFEAT_EFFECTS = Set.of("flame", "shatter", "storm", "tears");

    private ConfigChecks() {}

    static void requireConfig(ItemConfig config) {
        if (config == null) throw new InvalidItemConfigException("config가 null입니다.");
    }

    static void requireDisplayName(ItemConfig config) {
        if (isBlank(config.displayName())) {
            throw new InvalidItemConfigException("displayName은 필수입니다.");
        }
    }

    static void requireAssetKey(ItemConfig config) {
        if (!config.hasAsset()) {
            throw new InvalidItemConfigException("assetKey는 필수입니다.");
        }
        if (!SAFE_ASSET_KEY.matcher(config.assetKey()).matches()) {
            throw new InvalidItemConfigException("assetKey 형식이 올바르지 않습니다: " + config.assetKey());
        }
    }

    static void validateAssetKeyIfPresent(ItemConfig config) {
        if (config.hasAsset() && !SAFE_ASSET_KEY.matcher(config.assetKey()).matches()) {
            throw new InvalidItemConfigException("assetKey 형식이 올바르지 않습니다: " + config.assetKey());
        }
    }

    static void requireColors(ItemConfig config) {
        if (!config.hasColors()) throw new InvalidItemConfigException("colors는 필수입니다.");
        requireHex("colors.bg", config.colors().bg());
        requireHex("colors.lines", config.colors().lines());
        requireHex("colors.dots", config.colors().dots());
    }

    static void requireStone(ItemConfig config) {
        if (!config.hasStone()) throw new InvalidItemConfigException("stone은 필수입니다.");
        requireHex("stone.fill", config.stone().fill());
        requireHex("stone.stroke", config.stone().stroke());
        requireHex("stone.shine", config.stone().shine());
    }

    static void requireCharacter(ItemConfig config) {
        if (!config.hasCharacter()) throw new InvalidItemConfigException("character는 필수입니다.");
        requireHex("character.body", config.character().body());
        requireHex("character.accent", config.character().accent());
        if (config.character().face() == null || !FACE_KEY.matcher(config.character().face()).matches()) {
            throw new InvalidItemConfigException("character.face는 안전한 키워드여야 합니다: " + config.character().face());
        }
    }

    static void requireEffect(ItemConfig config) {
        if (!config.hasEffect()) throw new InvalidItemConfigException("effect는 필수입니다.");
        validateEffect(config.effect());
    }

    static void requireDefeatEffect(ItemConfig config) {
        if (!config.hasEffect()) throw new InvalidItemConfigException("effect는 필수입니다.");
        if (!VALID_DEFEAT_EFFECTS.contains(config.effect())) {
            throw new InvalidItemConfigException("지원하지 않는 패배 이펙트입니다: " + config.effect());
        }
    }

    /** STONE_SKIN이 애니메이션을 번들할 수 있으므로 effect가 있으면 키를 검증한다(없으면 무시). */
    static void validateEffectIfPresent(ItemConfig config) {
        if (config.hasEffect()) validateEffect(config.effect());
    }

    private static void validateEffect(String effect) {
        if (!VALID_EFFECTS.contains(effect)) {
            throw new InvalidItemConfigException("지원하지 않는 effect입니다: " + effect);
        }
    }

    static void validateFilter(ItemConfig.SvgFilter filter) {
        if (!VALID_FILTER_TYPES.contains(filter.type())) {
            throw new InvalidItemConfigException("filter.type이 올바르지 않습니다: " + filter.type());
        }
        if (!VALID_BLEND_MODES.contains(filter.blend())) {
            throw new InvalidItemConfigException("filter.blend가 올바르지 않습니다: " + filter.blend());
        }
        if (filter.freqX() < 0 || filter.freqY() < 0) {
            throw new InvalidItemConfigException("filter 주파수는 음수일 수 없습니다.");
        }
        if (filter.octaves() <= 0) {
            throw new InvalidItemConfigException("filter.octaves는 1 이상이어야 합니다.");
        }
    }

    private static void requireHex(String field, String value) {
        if (value == null || !HEX_COLOR.matcher(value).matches()) {
            throw new InvalidItemConfigException(field + "는 유효한 hex 색상이어야 합니다: " + value);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
