package com.dopamin.omok.shop.adapter.out.asset;

import com.dopamin.omok.shop.application.port.out.AssetPort;
import com.dopamin.omok.shop.domain.AssetResult;
import com.dopamin.omok.shop.domain.ItemType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * S3(또는 Cloudflare R2 등 S3 호환) 스토리지 어댑터 (스텁).
 * app.assets.storage=s3 로 설정하면 이 어댑터가 활성화된다.
 *
 * 전환 방법:
 *  1. application.yml 에 app.assets.storage=s3 추가
 *  2. build.gradle 에 S3 SDK 의존성 추가 (AWS SDK 또는 R2 호환 클라이언트)
 *  3. 아래 TODO 구현 — 객체 키: "{itemType}/{assetKey}.{ext}"
 */
@Component
@ConditionalOnProperty(name = "app.assets.storage", havingValue = "s3")
public class S3AssetAdapter implements AssetPort {

    @Override
    public AssetResult load(ItemType itemType, String assetKey) {
        // TODO: S3Presigner로 "{itemType}/{assetKey}" 객체의 서명 URL 생성
        // String key = itemType.name().toLowerCase() + "/" + assetKey;
        // String url = presign(bucket, key, expires);
        // return new AssetResult.SignedUrl(url);
        throw new UnsupportedOperationException("S3AssetAdapter is not yet implemented.");
    }
}
