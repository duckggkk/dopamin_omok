package com.dopamin.omok.shop.application.service;

import com.dopamin.omok.shop.application.port.out.LoadItemPort;
import com.dopamin.omok.shop.domain.Item;
import com.dopamin.omok.shop.domain.ItemType;
import com.dopamin.omok.shop.domain.validation.ItemConfigValidators;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 애플리케이션 시작 시 모든 아이템의 item_config를 타입별 검증기로 검증한다.
 * 잘못된 데이터가 있으면 기동을 중단(fail-fast)하여 운영 중 오류를 예방한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemConfigValidationRunner implements ApplicationRunner {

    private final LoadItemPort loadItemPort;
    private final ItemConfigValidators validators;

    @Override
    public void run(ApplicationArguments args) {
        int validated = 0;
        for (ItemType type : ItemType.values()) {
            List<Item> items = loadItemPort.findByType(type);
            for (Item item : items) {
                if (item.getItemConfig() == null) continue;
                try {
                    validators.validate(type, item.getItemConfig());
                    validated++;
                } catch (RuntimeException e) {
                    throw new IllegalStateException(
                            "아이템(id=" + item.getId() + ", name=" + item.getName()
                                    + ")의 item_config 검증 실패: " + e.getMessage(), e);
                }
            }
        }
        log.info("아이템 설정 검증 완료: {}개 config", validated);
    }
}
