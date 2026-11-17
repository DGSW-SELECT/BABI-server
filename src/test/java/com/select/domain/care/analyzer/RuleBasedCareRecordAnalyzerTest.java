package com.select.domain.care.analyzer;

import com.select.domain.care.entity.DiaperType;
import com.select.domain.care.entity.FeedingType;
import com.select.domain.care.entity.CareRecordType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedCareRecordAnalyzerTest {

    private final RuleBasedCareRecordAnalyzer analyzer = new RuleBasedCareRecordAnalyzer();
    private final LocalDateTime referenceTime = LocalDateTime.of(2026, 11, 3, 20, 30);

    @Test
    void analyzesFormulaFeedingWithAmountAndTime() {
        List<CareRecordDraft> records = analyzer.analyze("오후 2시에 분유 100ml 먹였어", referenceTime);

        assertThat(records).hasSize(1);
        CareRecordDraft record = records.get(0);
        assertThat(record.recordType()).isEqualTo(CareRecordType.FEEDING);
        assertThat(record.feedingType()).isEqualTo(FeedingType.FORMULA);
        assertThat(record.amountMl()).isEqualTo(100);
        assertThat(record.occurredAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 14, 0));
        assertThat(record.memo()).isEqualTo("오후 2시에 분유 100ml 먹였어");
    }

    @Test
    void analyzesBreastMilkFeedingWithKoreanHour() {
        List<CareRecordDraft> records = analyzer.analyze("두 시에 모유 수유했어", referenceTime);

        assertThat(records).hasSize(1);
        CareRecordDraft record = records.get(0);
        assertThat(record.recordType()).isEqualTo(CareRecordType.FEEDING);
        assertThat(record.feedingType()).isEqualTo(FeedingType.BREAST_MILK);
        assertThat(record.occurredAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 14, 0));
    }

    @Test
    void analyzesBabyFoodFeedingYesterday() {
        List<CareRecordDraft> records = analyzer.analyze("어제 저녁 7시에 이유식 먹였어", referenceTime);

        assertThat(records).hasSize(1);
        CareRecordDraft record = records.get(0);
        assertThat(record.recordType()).isEqualTo(CareRecordType.FEEDING);
        assertThat(record.feedingType()).isEqualTo(FeedingType.BABY_FOOD);
        assertThat(record.occurredAt()).isEqualTo(LocalDateTime.of(2026, 11, 2, 19, 0));
    }

    @Test
    void analyzesFeedingFromAmountWithoutFeedingKeyword() {
        List<CareRecordDraft> records = analyzer.analyze("100ml 먹였어", referenceTime);

        assertThat(records).hasSize(1);
        CareRecordDraft record = records.get(0);
        assertThat(record.recordType()).isEqualTo(CareRecordType.FEEDING);
        assertThat(record.feedingType()).isEqualTo(FeedingType.OTHER);
        assertThat(record.amountMl()).isEqualTo(100);
        assertThat(record.occurredAt()).isEqualTo(referenceTime);
    }

    @Test
    void analyzesSleepDurationWithHoursAndMinutes() {
        List<CareRecordDraft> records = analyzer.analyze("어제 밤 11시부터 2시간 30분 잤어", referenceTime);

        assertThat(records).hasSize(1);
        CareRecordDraft record = records.get(0);
        assertThat(record.recordType()).isEqualTo(CareRecordType.SLEEP);
        assertThat(record.durationMinutes()).isEqualTo(150);
        assertThat(record.occurredAt()).isEqualTo(LocalDateTime.of(2026, 11, 2, 23, 0));
    }

    @Test
    void durationHoursAreNotParsedAsClockTime() {
        List<CareRecordDraft> records = analyzer.analyze("2시간 잤어요", referenceTime);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).recordType()).isEqualTo(CareRecordType.SLEEP);
        assertThat(records.get(0).durationMinutes()).isEqualTo(120);
        assertThat(records.get(0).occurredAt()).isEqualTo(referenceTime);
    }

    @Test
    void analyzesSleepDurationInMinutes() {
        List<CareRecordDraft> records = analyzer.analyze("30분 잠들었어", referenceTime);

        assertThat(records).hasSize(1);
        CareRecordDraft record = records.get(0);
        assertThat(record.recordType()).isEqualTo(CareRecordType.SLEEP);
        assertThat(record.durationMinutes()).isEqualTo(30);
        assertThat(record.occurredAt()).isEqualTo(referenceTime);
    }

    @Test
    void analyzesStoolDiaper() {
        List<CareRecordDraft> records = analyzer.analyze("기저귀에 응가 했어", referenceTime);

        assertThat(records).hasSize(1);
        CareRecordDraft record = records.get(0);
        assertThat(record.recordType()).isEqualTo(CareRecordType.DIAPER);
        assertThat(record.diaperType()).isEqualTo(DiaperType.STOOL);
        assertThat(record.occurredAt()).isEqualTo(referenceTime);
    }

    @Test
    void analyzesMixedDiaper() {
        List<CareRecordDraft> records = analyzer.analyze("오줌이랑 응가 했어", referenceTime);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).diaperType()).isEqualTo(DiaperType.MIXED);
    }

    @Test
    void analyzesDiaperWithoutDetailAsOther() {
        List<CareRecordDraft> records = analyzer.analyze("기저귀 갈았어", referenceTime);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).recordType()).isEqualTo(CareRecordType.DIAPER);
        assertThat(records.get(0).diaperType()).isEqualTo(DiaperType.OTHER);
    }

    @Test
    void analyzesMultipleRecordsFromOneTranscript() {
        List<CareRecordDraft> records = analyzer.analyze("분유 120ml 먹이고 기저귀 갈았어", referenceTime);

        assertThat(records).extracting(CareRecordDraft::recordType)
                .containsExactly(CareRecordType.FEEDING, CareRecordType.DIAPER);
        assertThat(records.get(0).amountMl()).isEqualTo(120);
    }

    @Test
    void parsesColonTime() {
        List<CareRecordDraft> records = analyzer.analyze("14:30에 분유 80ml 먹였어", referenceTime);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).occurredAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 14, 30));
    }

    @Test
    void parsesMorningMeridiem() {
        List<CareRecordDraft> records = analyzer.analyze("오전 9시에 분유 90ml 먹였어", referenceTime);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).occurredAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 9, 0));
    }

    @Test
    void parsesNightTwelveAsMidnight() {
        List<CareRecordDraft> records = analyzer.analyze("밤 12시에 분유 먹였어", referenceTime);

        assertThat(records).hasSize(1);
        assertThat(records.getFirst().occurredAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 0, 0));
    }

    @Test
    void parsesDawnTimeAsEarlyMorning() {
        List<CareRecordDraft> records = analyzer.analyze("새벽 4시에 분유 60ml 먹였어", referenceTime);

        assertThat(records).hasSize(1);
        assertThat(records.getFirst().occurredAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 4, 0));
    }

    @Test
    void ignoresAmountWhenNoFeedingContext() {
        List<CareRecordDraft> records = analyzer.analyze("물 100ml 마셨어", referenceTime);

        assertThat(records).isEmpty();
    }

    @Test
    void returnsEmptyForUnrelatedTranscript() {
        List<CareRecordDraft> records = analyzer.analyze("오늘 기분이 좋아", referenceTime);

        assertThat(records).isEmpty();
    }

    @Test
    void returnsEmptyForBlankTranscript() {
        assertThat(analyzer.analyze("   ", referenceTime)).isEmpty();
        assertThat(analyzer.analyze(null, referenceTime)).isEmpty();
    }
}
