package com.select.domain.care.repository;

import com.select.domain.care.dto.request.CreateCareRecordRequest;
import com.select.domain.care.dto.request.UpdateFeedingRequest;
import com.select.domain.care.dto.request.UpdateTemperatureRequest;
import com.select.domain.care.dto.response.CareRecordResponse;
import com.select.domain.care.entity.CareRecordType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class CareRecordRepository {
    private static final String RECORDS = """
            SELECT r.*, u.nickname AS author_nickname FROM (
                SELECT 'FEEDING' AS record_type,id,baby_id,author_member_id,voice_input_id,occurred_at,
                       feeding_type,amount_ml,NULL AS sleep_end_at,NULL AS temperature_celsius,
                       NULL AS diaper_type,NULL AS cry_type,NULL AS confidence,note,created_at
                FROM feeding_records
                UNION ALL
                SELECT 'SLEEP',id,baby_id,author_member_id,voice_input_id,sleep_start_at,
                       NULL,NULL,sleep_end_at,NULL,NULL,NULL,NULL,note,created_at FROM sleep_records
                UNION ALL
                SELECT 'TEMPERATURE',id,baby_id,author_member_id,voice_input_id,measured_at,
                       NULL,NULL,NULL,temperature_celsius,NULL,NULL,NULL,note,created_at FROM temperature_records
                UNION ALL
                SELECT 'DIAPER',id,baby_id,author_member_id,voice_input_id,occurred_at,
                       NULL,NULL,NULL,NULL,diaper_type,NULL,NULL,note,created_at FROM diaper_records
                UNION ALL
                SELECT 'CRY',id,baby_id,author_member_id,NULL,occurred_at,
                       NULL,NULL,NULL,NULL,NULL,cry_type,confidence,note,created_at FROM cry_records
            ) r
            LEFT JOIN baby_members bm ON bm.id = r.author_member_id AND bm.baby_id = r.baby_id
            LEFT JOIN users u ON u.id = bm.user_id
            """;
    private static final String DAY = """
            WHERE r.baby_id = ? AND r.occurred_at < ?
              AND (r.occurred_at >= ? OR (r.record_type = 'SLEEP' AND (r.sleep_end_at > ? OR r.sleep_end_at IS NULL)))
            """;
    private static final RowMapper<CareRecordResponse> MAPPER = (rs, row) -> new CareRecordResponse(
            rs.getLong("id"), rs.getLong("baby_id"), rs.getObject("author_member_id", Long.class),
            rs.getString("author_nickname"), rs.getObject("voice_input_id", Long.class),
            CareRecordType.valueOf(rs.getString("record_type")), rs.getTimestamp("occurred_at").toLocalDateTime(),
            rs.getString("feeding_type"), rs.getObject("amount_ml", Integer.class),
            rs.getTimestamp("sleep_end_at") == null ? null : rs.getTimestamp("sleep_end_at").toLocalDateTime(),
            rs.getBigDecimal("temperature_celsius"), rs.getString("diaper_type"), rs.getString("cry_type"),
            rs.getBigDecimal("confidence"), rs.getString("note"), rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbc;

    public CareRecordRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CareRecordResponse> findDay(Long babyId, LocalDate date, CareRecordType type,
                                             Long authorMemberId, int limit, int offset) {
        Timestamp start = Timestamp.valueOf(date.atStartOfDay());
        return jdbc.query(RECORDS + DAY + """
                  AND (? IS NULL OR r.record_type = ?) AND (? IS NULL OR r.author_member_id = ?)
                ORDER BY r.occurred_at, r.record_type, r.id LIMIT ? OFFSET ?
                """, MAPPER, babyId, Timestamp.valueOf(date.plusDays(1).atStartOfDay()), start, start,
                type == null ? null : type.name(), type == null ? null : type.name(),
                authorMemberId, authorMemberId, limit, offset);
    }

    public boolean ownsVoiceInput(Long babyId, Long memberId, Long voiceInputId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM voice_inputs WHERE id = ? AND baby_id = ? AND baby_member_id = ?)
                """, Boolean.class, voiceInputId, babyId, memberId));
    }

    public Optional<CareRecordResponse> findById(Long babyId, CareRecordType type, Long recordId) {
        return jdbc.query(RECORDS + " WHERE r.baby_id = ? AND r.record_type = ? AND r.id = ?",
                MAPPER, babyId, type.name(), recordId).stream().findFirst();
    }

    public Optional<CareRecordResponse> updateFeeding(Long babyId, Long recordId, UpdateFeedingRequest request) {
        jdbc.update("""
                UPDATE feeding_records
                   SET occurred_at = COALESCE(?, occurred_at),
                       feeding_type = COALESCE(?, feeding_type),
                       amount_ml = COALESCE(?, amount_ml),
                       note = CASE WHEN ? IS NULL THEN note ELSE NULLIF(?, '') END
                 WHERE baby_id = ? AND id = ?
                """, request.occurredAt(), request.feedingType() == null ? null : request.feedingType().name(),
                request.amountMl(), request.note(), request.note(), babyId, recordId);
        return findById(babyId, CareRecordType.FEEDING, recordId);
    }

    public Optional<CareRecordResponse> updateSleep(Long babyId, Long recordId, LocalDateTime startAt,
                                                    LocalDateTime endAt, String note) {
        jdbc.update("""
                UPDATE sleep_records
                   SET sleep_start_at = ?, sleep_end_at = ?,
                       note = CASE WHEN ? IS NULL THEN note ELSE NULLIF(?, '') END
                 WHERE baby_id = ? AND id = ?
                """, startAt, endAt, note, note, babyId, recordId);
        return findById(babyId, CareRecordType.SLEEP, recordId);
    }

    public boolean deleteFeeding(Long babyId, Long recordId) {
        return jdbc.update("DELETE FROM feeding_records WHERE baby_id = ? AND id = ?", babyId, recordId) == 1;
    }

    public boolean deleteSleep(Long babyId, Long recordId) {
        return jdbc.update("DELETE FROM sleep_records WHERE baby_id = ? AND id = ?", babyId, recordId) == 1;
    }

    public Optional<CareRecordResponse> updateTemperature(Long babyId, Long recordId, UpdateTemperatureRequest request) {
        jdbc.update("""
                UPDATE temperature_records
                   SET measured_at = COALESCE(?, measured_at),
                       temperature_celsius = COALESCE(?, temperature_celsius),
                       note = CASE WHEN ? IS NULL THEN note ELSE NULLIF(?, '') END
                 WHERE baby_id = ? AND id = ?
                """, request.occurredAt(), request.temperatureCelsius(), request.note(), request.note(), babyId, recordId);
        return jdbc.query(RECORDS + " WHERE r.baby_id = ? AND r.record_type = 'TEMPERATURE' AND r.id = ?",
                MAPPER, babyId, recordId).stream().findFirst();
    }

    public boolean deleteTemperature(Long babyId, Long recordId) {
        return jdbc.update("DELETE FROM temperature_records WHERE baby_id = ? AND id = ?", babyId, recordId) == 1;
    }

    public CareRecordResponse save(Long babyId, Long memberId, CreateCareRecordRequest request) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("baby_id", babyId);
        values.put("author_member_id", memberId);
        values.put("note", request.note());
        if (request.recordType() != CareRecordType.CRY) values.put("voice_input_id", request.voiceInputId());
        String table = switch (request.recordType()) {
            case FEEDING -> {
                values.put("occurred_at", request.occurredAt());
                values.put("feeding_type", request.feedingType().name());
                values.put("amount_ml", request.amountMl());
                yield "feeding_records";
            }
            case SLEEP -> {
                values.put("sleep_start_at", request.occurredAt());
                values.put("sleep_end_at", request.sleepEndAt());
                yield "sleep_records";
            }
            case TEMPERATURE -> {
                values.put("measured_at", request.occurredAt());
                values.put("temperature_celsius", request.temperatureCelsius());
                yield "temperature_records";
            }
            case DIAPER -> {
                values.put("occurred_at", request.occurredAt());
                values.put("diaper_type", request.diaperType().name());
                yield "diaper_records";
            }
            case CRY -> {
                values.put("occurred_at", request.occurredAt());
                values.put("cry_type", request.cryType());
                values.put("confidence", request.confidence());
                yield "cry_records";
            }
        };
        String placeholders = String.join(",", java.util.Collections.nCopies(values.size(), "?"));
        String sql = "INSERT INTO " + table + " (" + String.join(",", values.keySet()) + ") VALUES (" + placeholders + ")";
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            int index = 1;
            for (Object value : values.values()) statement.setObject(index++, value);
            return statement;
        }, key);
        return jdbc.queryForObject(RECORDS + " WHERE r.baby_id = ? AND r.record_type = ? AND r.id = ?",
                MAPPER, babyId, request.recordType().name(), key.getKey().longValue());
    }
}
