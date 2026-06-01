package com.dopamin.omok.shop.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "items", indexes = {
        @Index(name = "idx_items_type", columnList = "item_type")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item {

    @Id
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 50)
    private ItemType itemType;

    @Column(length = 500)
    private String description;

    /** 코스메틱 렌더링/재생 메타데이터 (스킨·착수음 등, 그 외 null) */
    @Convert(converter = ItemConfigConverter.class)
    @Column(name = "item_config", columnDefinition = "json")
    private ItemConfig itemConfig;

    /** true면 가입 시 전원에게 자동 지급되는 기본 아이템 (뽑기 풀에서는 제외) */
    @Column(name = "default_grant", nullable = false)
    private boolean defaultGrant;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 사용자에게 노출할 이름. config의 표시명이 있으면 사용, 없으면 name. */
    public String getDisplayName() {
        if (itemConfig != null && itemConfig.displayName() != null && !itemConfig.displayName().isBlank()) {
            return itemConfig.displayName();
        }
        return name;
    }
}
