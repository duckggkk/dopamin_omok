package com.dopamin.omok.game.physical.adapter.out.persistence;

import com.dopamin.omok.game.physical.application.dto.PhysicalReplayData;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** PhysicalReplayData ↔ JSON 문자열 변환. physical_game_records.replay (JSON) 컬럼 매핑. */
@Converter
public class PhysicalReplayConverter implements AttributeConverter<PhysicalReplayData, String> {

    /**
     * 리플레이는 한 번 저장되면 계속 읽혀야 하는 '과거 기록'이라, 이후 코드 변경에 관대하게 읽는다.
     * - 모르는 필드 무시: 나중에 필드가 빠져도 옛 리플레이가 열린다.
     * - 모르는 enum 값은 null: 밸런스 조정으로 아이템 종류가 사라져도 그 판 전체를 잃지 않는다
     *   (해당 아이템만 null 로 비고, 보드·움직임은 정상 재생).
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES) 
            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL); //모르는 값은 null

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
