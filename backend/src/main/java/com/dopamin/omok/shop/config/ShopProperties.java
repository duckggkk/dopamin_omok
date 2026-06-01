package com.dopamin.omok.shop.config;

import com.dopamin.omok.shop.domain.ItemType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Optional;

/**
 * 상점 비즈니스 설정 (충전 패키지, 뽑기 상자).
 * application.yml 의 app.shop 에서 주입된다.
 * 가격/구성 변경 시 코드 수정 없이 설정만 바꾸면 된다.
 */
@ConfigurationProperties(prefix = "app.shop")
public record ShopProperties(
        List<CurrencyPackage> packages,
        List<GachaBox> gacha
) {
    public record CurrencyPackage(String id, int currency, int priceKrw) {}

    public record GachaBox(ItemType type, String name, int price) {}

    public Optional<CurrencyPackage> findPackage(String id) {
        return packages.stream()
                .filter(p -> p.id().equalsIgnoreCase(id))
                .findFirst();
    }

    public Optional<GachaBox> findBox(ItemType type) {
        return gacha.stream()
                .filter(b -> b.type() == type)
                .findFirst();
    }
}
