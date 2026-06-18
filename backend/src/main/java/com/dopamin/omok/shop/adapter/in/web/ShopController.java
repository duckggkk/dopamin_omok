package com.dopamin.omok.shop.adapter.in.web;

import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.global.security.userdetails.CustomUserDetails;
import com.dopamin.omok.shop.adapter.in.web.dto.ChargeCurrencyRequest;
import com.dopamin.omok.shop.adapter.in.web.dto.EquipItemRequest;
import com.dopamin.omok.shop.adapter.in.web.dto.OpenGachaRequest;
import com.dopamin.omok.shop.adapter.in.web.dto.UnequipItemRequest;
import com.dopamin.omok.shop.application.dto.*;
import com.dopamin.omok.shop.application.port.in.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopController {

    private final GetShopInfoUseCase getShopInfoUseCase;
    private final ChargeCurrencyUseCase chargeCurrencyUseCase;
    private final OpenGachaUseCase openGachaUseCase;
    private final GetInventoryUseCase getInventoryUseCase;
    private final EquipItemUseCase equipItemUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<ShopInfoResponse>> getShopInfo() {
        return ResponseEntity.ok(ApiResponse.success(getShopInfoUseCase.getShopInfo()));
    }

    @PostMapping("/currency/charge")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> chargeCurrency(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChargeCurrencyRequest request) {
        int newBalance = chargeCurrencyUseCase.chargeCurrency(
                userDetails.getId(), request.packageId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("currency", newBalance)));
    }

    @PostMapping("/gacha/open")
    public ResponseEntity<ApiResponse<GachaResultResponse>> openBox(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody OpenGachaRequest request) {
        GachaResultResponse result = openGachaUseCase.openBox(
                userDetails.getId(), request.boxType());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/inventory")
    public ResponseEntity<ApiResponse<InventoryResponse>> getInventory(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                getInventoryUseCase.getInventory(userDetails.getId())));
    }

    @PostMapping("/equip")
    public ResponseEntity<ApiResponse<Void>> equipItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody EquipItemRequest request) {
        equipItemUseCase.equipItem(userDetails.getId(), request.itemId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** 장착 해제 — 해당 카테고리를 기본값으로 되돌린다(예: 바둑알 스킨 → 기본 스킨). */
    @PostMapping("/unequip")
    public ResponseEntity<ApiResponse<Void>> unequipItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UnequipItemRequest request) {
        equipItemUseCase.unequip(userDetails.getId(), request.itemType());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
