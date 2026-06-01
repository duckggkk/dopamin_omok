package com.dopamin.omok.shop.domain;

import com.dopamin.omok.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_active_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "item_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserActiveItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 50)
    private ItemType itemType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    public static UserActiveItem of(User user, ItemType itemType, Item item) {
        UserActiveItem uai = new UserActiveItem();
        uai.user = user;
        uai.itemType = itemType;
        uai.item = item;
        return uai;
    }

    public void updateItem(Item item) {
        this.item = item;
    }
}
