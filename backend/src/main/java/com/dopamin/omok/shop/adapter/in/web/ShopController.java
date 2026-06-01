package com.dopamin.omok.shop.adapter.in.web;

import com.dopamin.omok.global.common.response.ApiResponse;
import com.dopamin.omok.global.security.userdetails.CustomUserDetails;
import com.dopamin.omok.shop.application.dto.*;
import com.dopamin.omok.shop.application.port.in.*;
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
            @RequestBody Map<String, String> body) {
        int newBalance = chargeCurrencyUseCase.chargeCurrency(
                userDetails.getId(), body.get("packageId"));
        return ResponseEntity.ok(ApiResponse.success(Map.of("currency", newBalance)));
    }

    @PostMapping("/gacha/open")
    public ResponseEntity<ApiResponse<GachaResultResponse>> openBox(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, String> body) {
        GachaResultResponse result = openGachaUseCase.openBox(
                userDetails.getId(), body.get("boxType"));
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
            @RequestBody Map<String, Long> body) {
        equipItemUseCase.equipItem(userDetails.getId(), body.get("itemId"));
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
