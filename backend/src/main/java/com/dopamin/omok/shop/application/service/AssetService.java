package com.dopamin.omok.shop.application.service;

import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.shop.application.port.in.GetAssetUseCase;
import com.dopamin.omok.shop.application.port.out.AssetPort;
import com.dopamin.omok.shop.application.port.out.LoadItemPort;
import com.dopamin.omok.shop.application.port.out.LoadUserItemPort;
import com.dopamin.omok.shop.domain.AssetResult;
import com.dopamin.omok.shop.domain.Item;
import com.dopamin.omok.shop.domain.ItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보호 에셋 제공 서비스 (스킨 이미지, 착수음 오디오 등 모든 타입 공통).
 * assetKey를 가진 아이템만 대상이며, 소유권 검증 후 스토리지에 위임한다.
 * 유효성/소유 매핑은 모두 DB 기반 — 하드코딩 없음.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetService implements GetAssetUseCase {

    private final AssetPort assetPort;               // classpath or S3 — 설정으로 전환
    private final LoadItemPort loadItemPort;
    private final LoadUserItemPort loadUserItemPort;

    @Override
    public AssetResult getAsset(Long userId, ItemType itemType, String assetKey) {
        Item item = findByAssetKey(itemType, assetKey);

        if (!loadUserItemPort.existsByUserIdAndItemId(userId, item.getId())) {
            throw new OmokException(ErrorCode.ITEM_NOT_OWNED);
        }

        // DB에 저장된 검증된 assetKey로 로드 (요청 파라미터 직접 사용 안 함 → 경로 조작 방지)
        return assetPort.load(itemType, item.getItemConfig().assetKey());
    }

    private Item findByAssetKey(ItemType itemType, String assetKey) {
        return loadItemPort.findByType(itemType).stream()
                .filter(item -> item.getItemConfig() != null
                        && assetKey.equals(item.getItemConfig().assetKey()))
                .findFirst()
                .orElseThrow(() -> new OmokException(ErrorCode.ITEM_NOT_FOUND));
    }
}
