package com.dopamin.omok.game.physical.adapter.out.persistence;

import com.dopamin.omok.game.physical.application.dto.PhysicalReplayData;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** PhysicalReplayData ↔ JSON 문자열 변환. physical_game_records.replay (JSON) 컬럼 매핑. */
@Converter
public class PhysicalReplayConverter implements AttributeConverter<PhysicalReplayData, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(PhysicalReplayData attribute) {
        if (attribute == null) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("PhysicalReplayData 직렬화 실패", e);
        }
    }

    @Override
    public PhysicalReplayData convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return MAPPER.readValue(dbData, PhysicalReplayData.class);
        } catch (Exception e) {
            throw new IllegalStateException("PhysicalReplayData 역직렬화 실패", e);
        }
    }
}
