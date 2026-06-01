package com.dopamin.omok.shop.adapter.out.asset;

import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.shop.application.port.out.AssetPort;
import com.dopamin.omok.shop.domain.AssetResult;
import com.dopamin.omok.shop.domain.ItemType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 기본 스토리지 어댑터.
 * app.assets.storage=classpath (또는 미설정 시 기본값)
 *
 * 에셋은 classpath:assets/{itemType}/{assetKey}.{ext} 에 저장되며 웹으로 직접 노출되지 않는다.
 * 예) assets/board_skin/white_marble.webp, assets/stone_sound/wood.mp3
 *
 * 디렉터리는 ItemType 이름(소문자)에서 자동 도출되므로 타입 추가 시 코드 변경 불필요.
 */
@Component
@ConditionalOnProperty(name = "app.assets.storage", havingValue = "classpath", matchIfMissing = true)
public class ClasspathAssetAdapter implements AssetPort {

    // 확장자 → MIME 타입 (탐색 우선순위 순서)
    private static final Map<String, String> CONTENT_TYPES = new LinkedHashMap<>();
    static {
        CONTENT_TYPES.put("svg",  "image/svg+xml");
        CONTENT_TYPES.put("webp", "image/webp");
        CONTENT_TYPES.put("png",  "image/png");
        CONTENT_TYPES.put("jpg",  "image/jpeg");
        CONTENT_TYPES.put("mp3",  "audio/mpeg");
        CONTENT_TYPES.put("ogg",  "audio/ogg");
        CONTENT_TYPES.put("wav",  "audio/wav");
        CONTENT_TYPES.put("webm", "audio/webm");
        CONTENT_TYPES.put("m4a",  "audio/mp4");
    }

    @Override
    public AssetResult load(ItemType itemType, String assetKey) {
        String dir = "assets/" + itemType.name().toLowerCase() + "/";

        for (Map.Entry<String, String> entry : CONTENT_TYPES.entrySet()) {
            ClassPathResource resource = new ClassPathResource(dir + assetKey + "." + entry.getKey());
            if (resource.exists()) {
                try {
                    return new AssetResult.Data(resource.getInputStream().readAllBytes(), entry.getValue());
                } catch (IOException e) {
                    throw new OmokException(ErrorCode.INTERNAL_SERVER_ERROR);
                }
            }
        }
        throw new OmokException(ErrorCode.ITEM_NOT_FOUND);
    }
}
