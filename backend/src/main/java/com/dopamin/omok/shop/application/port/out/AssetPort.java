package com.dopamin.omok.shop.application.port.out;

import com.dopamin.omok.shop.domain.AssetResult;
import com.dopamin.omok.shop.domain.ItemType;

/**
 * 보호 에셋 저장소 추상화 포트 (스킨 이미지, 착수음 오디오 등 공통).
 * classpath, S3, GCS 등 어떤 스토리지든 이 인터페이스만 구현하면 전환 가능.
 */
public interface AssetPort {
    AssetResult load(ItemType itemType, String assetKey);
}
