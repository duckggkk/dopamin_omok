package com.dopamin.omok.shop.adapter.in.web;

import com.dopamin.omok.global.common.exception.ErrorCode;
import com.dopamin.omok.global.common.exception.OmokException;
import com.dopamin.omok.global.security.principal.AuthUser;
import com.dopamin.omok.shop.application.port.in.GetAssetUseCase;
import com.dopamin.omok.shop.domain.AssetResult;
import com.dopamin.omok.shop.domain.ItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 보호 에셋 엔드포인트.
 * GET /assets/{itemType}/{assetKey}  (예: /assets/board_skin/white_marble, /assets/stone_sound/wood)
 * 인증 + 소유권 검증 후 이미지/오디오 바이너리(또는 S3 서명 URL)를 반환한다.
 */
@RestController
@RequestMapping("/assets")
@RequiredArgsConstructor
public class AssetController {

    private final GetAssetUseCase getAssetUseCase;

    @GetMapping("/{itemType}/{assetKey}")
    public ResponseEntity<byte[]> getAsset(
            @PathVariable String itemType,
            @PathVariable String assetKey,
            @AuthenticationPrincipal AuthUser userDetails) {

        ItemType type = parseType(itemType);
        AssetResult result = getAssetUseCase.getAsset(userDetails.id(), type, assetKey);

        return switch (result) {
            // classpath 바이너리 — 이미지/오디오 데이터를 직접 스트리밍
            case AssetResult.Data d -> ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(d.contentType()))
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                    .body(d.bytes());

            // S3 서명 URL — JSON으로 URL 반환, 프론트가 직접 로드
            case AssetResult.SignedUrl s -> ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(("{\"url\":\"" + s.url() + "\"}").getBytes(StandardCharsets.UTF_8));
        };
    }

    private ItemType parseType(String itemType) {
        try {
            return ItemType.valueOf(itemType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new OmokException(ErrorCode.ITEM_NOT_FOUND);
        }
    }
}
