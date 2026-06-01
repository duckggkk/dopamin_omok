package com.dopamin.omok.shop.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * ItemConfig 값 객체 ↔ JSON 문자열 변환.
 * items.item_config (JSON) 컬럼 매핑에 사용된다.
 */
@Converter
public class ItemConfigConverter implements AttributeConverter<ItemConfig, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(ItemConfig attribute) {
        if (attribute == null) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("ItemConfig 직렬화 실패", e);
        }
    }

    @Override
    public ItemConfig convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return MAPPER.readValue(dbData, ItemConfig.class);
        } catch (Exception e) {
            throw new IllegalStateException("ItemConfig 역직렬화 실패: " + dbData, e);
        }
    }
}
