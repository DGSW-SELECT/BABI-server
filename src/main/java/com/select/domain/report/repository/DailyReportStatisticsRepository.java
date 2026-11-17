package com.select.domain.report.repository;

import com.select.domain.report.dto.response.DailyReportResponse.SevenDayAverage;
import com.select.domain.report.dto.response.DailyReportFacts;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class DailyReportStatisticsRepository {

    private static final int DAYS = 7;

    private final JdbcTemplate jdbcTemplate;

    public DailyReportStatisticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SevenDayAverage findAverage(Long babyId, LocalDate reportDate) {
        LocalDateTime start = reportDate.minusDays(DAYS - 1).atStartOfDay();
        LocalDateTime end = reportDate.plusDays(1).atStartOfDay();

        Double feedingAverage = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(amount_ml), 0) / 7.0
                FROM feeding_records
                WHERE baby_id = ?
                  AND occurred_at >= ?
                  AND occurred_at < ?
                """, Double.class, babyId, Timestamp.valueOf(start), Timestamp.valueOf(end));

        List<Long> sleepMinutes = jdbcTemplate.query("""
                SELECT sleep_start_at, sleep_end_at
                FROM sleep_records
                WHERE baby_id = ?
                  AND sleep_start_at < ?
                  AND sleep_end_at > ?
                  AND sleep_end_at IS NOT NULL
                """, (rs, rowNum) -> Duration.between(
                max(rs.getTimestamp("sleep_start_at").toLocalDateTime(), start),
                min(rs.getTimestamp("sleep_end_at").toLocalDateTime(), end)
        ).toMinutes(), babyId, Timestamp.valueOf(end), Timestamp.valueOf(start));

        double sleepAverage = sleepMinutes.stream()
                .filter(minutes -> minutes > 0)
                .mapToLong(Long::longValue)
                .sum() / (double) DAYS;

        Double temperatureAverage = jdbcTemplate.queryForObject("""
                SELECT AVG(temperature_celsius)
                FROM temperature_records
                WHERE baby_id = ?
                  AND measured_at >= ?
                  AND measured_at < ?
                """, Double.class, babyId, Timestamp.valueOf(start), Timestamp.valueOf(end));

        return new SevenDayAverage(feedingAverage, sleepAverage, temperatureAverage);
    }

    public DailyReportFacts findDailyFacts(Long babyId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        var feeding = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS record_count, COUNT(amount_ml) AS measured_count,
                       COALESCE(SUM(amount_ml),0) AS total_ml
                FROM feeding_records WHERE baby_id=? AND occurred_at>=? AND occurred_at<?
                """, babyId, Timestamp.valueOf(start), Timestamp.valueOf(end));
        var temperature = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS record_count, AVG(temperature_celsius) AS average_value,
                       MIN(temperature_celsius) AS min_value, MAX(temperature_celsius) AS max_value
                FROM temperature_records WHERE baby_id=? AND measured_at>=? AND measured_at<?
                """, babyId, Timestamp.valueOf(start), Timestamp.valueOf(end));
        List<Long> sleeps = jdbcTemplate.query("""
                SELECT sleep_start_at,sleep_end_at FROM sleep_records
                WHERE baby_id=? AND sleep_start_at<? AND sleep_end_at>? AND sleep_end_at IS NOT NULL
                """, (rs, row) -> Duration.between(max(rs.getTimestamp("sleep_start_at").toLocalDateTime(), start),
                        min(rs.getTimestamp("sleep_end_at").toLocalDateTime(), end)).toMinutes(),
                babyId, Timestamp.valueOf(end), Timestamp.valueOf(start));
        Long ongoing = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sleep_records WHERE baby_id=? AND sleep_start_at<? AND sleep_end_at IS NULL
                """, Long.class, babyId, Timestamp.valueOf(end));
        Long diapers = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM diaper_records WHERE baby_id=? AND occurred_at>=? AND occurred_at<?
                """, Long.class, babyId, Timestamp.valueOf(start), Timestamp.valueOf(end));
        Long cries = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM cry_records WHERE baby_id=? AND occurred_at>=? AND occurred_at<?
                """, Long.class, babyId, Timestamp.valueOf(start), Timestamp.valueOf(end));
        return new DailyReportFacts(date, ((Number) feeding.get("record_count")).longValue(),
                ((Number) feeding.get("measured_count")).longValue(), ((Number) feeding.get("total_ml")).longValue(),
                sleeps.size(), ongoing, sleeps.stream().filter(value -> value > 0).mapToLong(Long::longValue).sum(),
                ((Number) temperature.get("record_count")).longValue(), decimal(temperature.get("average_value")),
                decimal(temperature.get("min_value")), decimal(temperature.get("max_value")), diapers, cries);
    }

    private Double decimal(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }

    private LocalDateTime max(LocalDateTime left, LocalDateTime right) {
        return left.isAfter(right) ? left : right;
    }

    private LocalDateTime min(LocalDateTime left, LocalDateTime right) {
        return left.isBefore(right) ? left : right;
    }
}
