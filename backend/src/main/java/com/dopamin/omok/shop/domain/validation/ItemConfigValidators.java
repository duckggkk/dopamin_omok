package com.dopamin.omok.shop.domain.validation;

import com.dopamin.omok.shop.domain.ItemConfig;
import com.dopamin.omok.shop.domain.ItemType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 등록된 ItemConfigValidator들을 타입별로 모아 디스패치하는 레지스트리.
 * Spring이 모든 ItemConfigValidator 빈을 주입하므로,
 * 새 검증 전략을 추가해도 이 클래스는 수정 불필요 (OCP).
 */
@Component
public class ItemConfigValidators {

    private final Map<ItemType, ItemConfigValidator> byType;

    public ItemConfigValidators(List<ItemConfigValidator> validators) {
        this.byType = validators.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ItemConfigValidator::supportedType, Function.identity()));
    }

    /**
     * 해당 타입에 등록된 검증기로 config를 검증한다.
     * config를 갖는 타입인데 검증기가 없으면 설정 오류로 간주해 예외를 던진다.
     */
    public void validate(ItemType type, ItemConfig config) {
        if (config == null) return;   // config 없는 타입(DEFEAT_MESSAGE 등)은 검증 대상 아님
        ItemConfigValidator validator = byType.get(type);
        if (validator == null) {
            throw new ItemConfigValidator.InvalidItemConfigException(
                    type + " 타입의 config 검증기가 등록되지 않았습니다.");
        }
        validator.validate(config);
    }
}
