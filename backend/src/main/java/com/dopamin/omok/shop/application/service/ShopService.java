package com.dopamin.omok.shop.application.service;

import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.shop.application.dto.*;
import com.dopamin.omok.shop.application.port.in.*;
import com.dopamin.omok.shop.application.port.out.*;
import com.dopamin.omok.shop.config.ShopProperties;
import com.dopamin.omok.shop.domain.*;
import com.dopamin.omok.user.application.port.out.LoadUserPort;
import com.dopamin.omok.user.application.port.out.SaveUserPort;
import com.dopamin.omok.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopService implements ChargeCurrencyUseCase, OpenGachaUseCase,
        GetInventoryUseCase, EquipItemUseCase, GetShopInfoUseCase {

    private final ShopProperties shopProperties;
    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final LoadItemPort loadItemPort;
    private final LoadUserItemPort loadUserItemPort;
    private final SaveUserItemPort saveUserItemPort;
    private final LoadUserActiveItemPort loadUserActiveItemPort;
    private final SaveUserActiveItemPort saveUserActiveItemPort;
    private final SecureRandom random = new SecureRandom();

    @Override
    public ShopInfoResponse getShopInfo() {
        List<CurrencyPackageInfo> packages = shopProperties.packages().stream()
                .map(p -> new CurrencyPackageInfo(p.id(), p.currency(), p.priceKrw(), p.currency() + "돌"))
                .toList();

        List<GachaBoxInfo> boxes = shopProperties.gacha().stream()
                .map(box -> {
                    // 상자에서 나올 수 있는 아이템 목록 (기본 지급 아이템 제외, DB 동적 조회)
                    List<String> possibleItems = loadItemPort.findGachaPoolByType(box.type()).stream()
                            .map(Item::getDisplayName)
                            .toList();
                    return new GachaBoxInfo(box.type().name(), box.name(), box.price(),
                            box.type(), possibleItems);
                })
                .toList();

        return new ShopInfoResponse(packages, boxes);
    }

    @Override
    @Transactional
    public int chargeCurrency(Long userId, String packageId) {
        // TODO(결제): 현재는 결제 검증 없이 재화를 지급하는 스텁이다.
        //  실서비스 전환 시 PG(아임포트/토스페이먼츠 등) 결제 영수증 검증을 선행해야 한다.
        //  그때까지는 직접 충전을 설정 플래그로 게이트하여 운영(prod)에서는 비활성화한다.
        if (!shopProperties.directChargeEnabled()) {
            throw new OmokException(ErrorCode.DIRECT_CHARGE_DISABLED);
        }

        ShopProperties.CurrencyPackage pack = shopProperties.findPackage(packageId)
                .orElseThrow(() -> new OmokException(ErrorCode.INVALID_REQUEST));

        User user = findUser(userId);
        user.chargeCurrency(pack.currency());
        saveUserPort.save(user);
        return user.getCurrency();
    }

    @Override
    @Transactional
    public GachaResultResponse openBox(Long userId, String boxType) {
        ItemType type = parseItemType(boxType);
        ShopProperties.GachaBox box = shopProperties.findBox(type)
                .orElseThrow(() -> new OmokException(ErrorCode.INVALID_REQUEST));

        // 상자 풀은 해당 타입의 뽑기 대상 아이템 (기본 지급 아이템 제외, 동일 확률)
        List<Item> pool = loadItemPort.findGachaPoolByType(type);
        if (pool.isEmpty()) throw new OmokException(ErrorCode.ITEM_NOT_FOUND);

        User user = findUser(userId);
        if (user.getCurrency() < box.price()) throw new OmokException(ErrorCode.INSUFFICIENT_CURRENCY);

        Item picked = pool.get(random.nextInt(pool.size()));
        boolean isDuplicate = loadUserItemPort.existsByUserIdAndItemId(userId, picked.getId());

        user.spendCurrency(box.price());
        saveUserPort.save(user);

        if (!isDuplicate) {
            saveUserItemPort.save(UserItem.of(user, picked));
        }

        return new GachaResultResponse(ItemResponse.from(picked), user.getCurrency(), isDuplicate);
    }

    @Override
    public InventoryResponse getInventory(Long userId) {
        User user = findUser(userId);

        List<ItemResponse> items = loadUserItemPort.findUserItemsByUserId(userId)
                .stream()
                .map(ui -> ItemResponse.from(ui.getItem()))
                .toList();

        Map<String, ItemResponse> activeItems = new HashMap<>();
        loadUserActiveItemPort.findUserActiveItemsByUserId(userId)
                .forEach(ua -> activeItems.put(ua.getItemType().name(), ItemResponse.from(ua.getItem())));

        return new InventoryResponse(user.getCurrency() != null ? user.getCurrency() : 0, items, activeItems);
    }

    @Override
    @Transactional
    public void equipItem(Long userId, Long itemId) {
        if (!loadUserItemPort.existsByUserIdAndItemId(userId, itemId)) {
            throw new OmokException(ErrorCode.ITEM_NOT_OWNED);
        }

        Item item = loadItemPort.findById(itemId)
                .orElseThrow(() -> new OmokException(ErrorCode.ITEM_NOT_FOUND));

        User user = findUser(userId);

        loadUserActiveItemPort.findByUserIdAndItemType(userId, item.getItemType())
                .ifPresentOrElse(
                        ua -> { ua.updateItem(item); saveUserActiveItemPort.save(ua); },
                        () -> saveUserActiveItemPort.save(UserActiveItem.of(user, item.getItemType(), item))
                );
    }

    private ItemType parseItemType(String boxType) {
        try {
            return ItemType.valueOf(boxType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new OmokException(ErrorCode.INVALID_REQUEST);
        }
    }

    private User findUser(Long userId) {
        return loadUserPort.findById(userId)
                .orElseThrow(() -> new OmokException(ErrorCode.USER_NOT_FOUND));
    }
}
