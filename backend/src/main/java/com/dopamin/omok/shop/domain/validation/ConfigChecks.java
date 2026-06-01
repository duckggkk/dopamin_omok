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
    private static final Set<String> VALID_FILTER_TYPES = Set.of("fractalNoise", "turbulence");
    private static final Set<String> VALID_BLEND_MODES =
            Set.of("normal", "multiply", "screen", "overlay", "darken", "lighten", "soft-light", "hard-light");

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
