package com.dopamin.omok.shop.domain;

import com.dopamin.omok.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "item_id"}),
        indexes = @Index(name = "idx_user_items_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private LocalDateTime acquiredAt;

    public static UserItem of(User user, Item item) {
        UserItem ui = new UserItem();
        ui.user = user;
        ui.item = item;
        ui.acquiredAt = LocalDateTime.now();
        return ui;
    }
}
