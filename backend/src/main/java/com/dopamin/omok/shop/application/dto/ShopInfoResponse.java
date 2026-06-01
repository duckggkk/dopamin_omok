package com.dopamin.omok.shop.application.dto;

import java.util.List;

public record ShopInfoResponse(List<CurrencyPackageInfo> packages, List<GachaBoxInfo> boxes) {}
