package com.select.domain.parenting.analyzer;

import com.select.domain.parenting.entity.DiaperType;
import com.select.domain.parenting.entity.FeedingType;
import com.select.domain.parenting.entity.ParentingRecordType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RuleBasedParentingRecordAnalyzer implements ParentingRecordAnalyzer {

    private static final Pattern COLON_TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final Pattern CLOCK_TIME_PATTERN = Pattern.compile(
            "(오전|오후|아침|점심|저녁|밤)?\\s*"
                    + "(열한|열두|한|두|세|네|다섯|여섯|일곱|여덟|아홉|열|\\d{1,2})\\s*시"
                    + "(?:\\s*(\\d{1,2})\\s*분)?"
    );
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(?:ml|미리리터|밀리리터|cc)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HOUR_DURATION_PATTERN = Pattern.compile(
            "(\\d+)\\s*시간(?:\\s*(\\d+)\\s*분)?"
    );
    private static final Pattern MINUTE_DURATION_PATTERN = Pattern.compile(
            "(\\d+)\\s*분\\s*(?:동안|정도)?\\s*(?:잠|잤|수면)"
    );
    private static final Pattern SLEEP_MINUTE_DURATION_PATTERN = Pattern.compile(
            "(?:잠|잤|수면)[^0-9]{0,6}(\\d+)\\s*분"
    );

    private static final Map<String, Integer> KOREAN_HOURS = Map.ofEntries(
            Map.entry("한", 1),
            Map.entry("두", 2),
            Map.entry("세", 3),
            Map.entry("네", 4),
            Map.entry("다섯", 5),
            Map.entry("여섯", 6),
            Map.entry("일곱", 7),
            Map.entry("여덟", 8),
            Map.entry("아홉", 9),
            Map.entry("열", 10),
            Map.entry("열한", 11),
            Map.entry("열두", 12)
    );

    @Override
    public List<ParentingRecordDraft> analyze(String transcript, LocalDateTime referenceTime) {
        if (!StringUtils.hasText(transcript)) {
            return List.of();
        }

        String text = transcript.trim();
        LocalDateTime baseTime = referenceTime == null ? LocalDateTime.now() : referenceTime;
        LocalDateTime occurredAt = parseOccurredAt(text, baseTime);
        List<ParentingRecordDraft> drafts = new ArrayList<>();

        Integer amountMl = parseAmountMl(text);
        if (isFeeding(text, amountMl)) {
            drafts.add(new ParentingRecordDraft(
                    ParentingRecordType.FEEDING,
                    occurredAt,
                    amountMl,
                    null,
                    parseFeedingType(text),
                    null,
                    text
            ));
        }

        if (isSleep(text)) {
            drafts.add(new ParentingRecordDraft(
                    ParentingRecordType.SLEEP,
                    occurredAt,
                    null,
                    parseDurationMinutes(text),
                    null,
                    null,
                    text
            ));
        }

        if (isDiaper(text)) {
            drafts.add(new ParentingRecordDraft(
                    ParentingRecordType.DIAPER,
                    occurredAt,
                    null,
                    null,
                    null,
                    parseDiaperType(text),
                    text
            ));
        }

        return List.copyOf(drafts);
    }

    private LocalDateTime parseOccurredAt(String text, LocalDateTime referenceTime) {
        LocalDate date = referenceTime.toLocalDate();

        if (text.contains("그저께") || text.contains("그제")) {
            date = date.minusDays(2);
        } else if (text.contains("어제")) {
            date = date.minusDays(1);
        }

        Matcher colonMatcher = COLON_TIME_PATTERN.matcher(text);
        if (colonMatcher.find()) {
            int hour = parseInt(colonMatcher.group(1));
            int minute = parseInt(colonMatcher.group(2));
            if (isValidTime(hour, minute)) {
                return date.atTime(hour, minute);
            }
        }

        Matcher clockMatcher = CLOCK_TIME_PATTERN.matcher(text);
        if (clockMatcher.find()) {
            Integer hour = parseHour(clockMatcher.group(2));
            int minute = clockMatcher.group(3) == null ? 0 : parseInt(clockMatcher.group(3));
            if (hour != null && isValidTime(hour, minute)) {
                return date.atTime(applyMeridiem(hour, clockMatcher.group(1), referenceTime), minute);
            }
        }

        return referenceTime;
    }

    private int applyMeridiem(int hour, String meridiem, LocalDateTime referenceTime) {
        if (meridiem == null) {
            if (hour < 12
                    && referenceTime.getHour() >= 12
                    && hour + 12 <= referenceTime.getHour()) {
                return hour + 12;
            }
            return hour;
        }

        if (meridiem.equals("오전") || meridiem.equals("아침")) {
            return hour == 12 ? 0 : hour;
        }

        return hour < 12 ? hour + 12 : hour;
    }

    private Integer parseHour(String value) {
        if (value.chars().allMatch(Character::isDigit)) {
            return parseInt(value);
        }
        return KOREAN_HOURS.get(value);
    }

    private Integer parseAmountMl(String text) {
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        int amount = (int) Math.round(Double.parseDouble(matcher.group(1)));
        return amount > 0 ? amount : null;
    }

    private Integer parseDurationMinutes(String text) {
        Matcher hourMatcher = HOUR_DURATION_PATTERN.matcher(text);
        if (hourMatcher.find()) {
            int minutes = parseInt(hourMatcher.group(1)) * 60;
            if (hourMatcher.group(2) != null) {
                minutes += parseInt(hourMatcher.group(2));
            }
            if (minutes > 0) {
                return minutes;
            }
        }

        Matcher minuteMatcher = MINUTE_DURATION_PATTERN.matcher(text);
        if (minuteMatcher.find()) {
            int minutes = parseInt(minuteMatcher.group(1));
            if (minutes > 0) {
                return minutes;
            }
        }

        Matcher sleepMinuteMatcher = SLEEP_MINUTE_DURATION_PATTERN.matcher(text);
        if (sleepMinuteMatcher.find()) {
            int minutes = parseInt(sleepMinuteMatcher.group(1));
            if (minutes > 0) {
                return minutes;
            }
        }

        return null;
    }

    private boolean isFeeding(String text, Integer amountMl) {
        return containsAny(text, "분유", "모유", "수유", "이유식", "젖병", "직수", "퓨레", "죽", "밥")
                || (amountMl != null && text.contains("먹"));
    }

    private boolean isSleep(String text) {
        return containsAny(text, "잠을", "잠이", "잠들", "낮잠", "밤잠", "잤", "수면", "재웠");
    }

    private boolean isDiaper(String text) {
        return containsAny(text, "기저귀", "응가", "대변", "오줌", "소변", "쉬야", "똥");
    }

    private FeedingType parseFeedingType(String text) {
        if (text.contains("분유")) {
            return FeedingType.FORMULA;
        }
        if (containsAny(text, "모유", "직수")) {
            return FeedingType.BREAST_MILK;
        }
        if (containsAny(text, "이유식", "퓨레", "죽", "밥")) {
            return FeedingType.BABY_FOOD;
        }
        return FeedingType.OTHER;
    }

    private DiaperType parseDiaperType(String text) {
        boolean urine = containsAny(text, "오줌", "소변", "쉬야", "쉬했", "쉬 했");
        boolean stool = containsAny(text, "응가", "대변", "똥");

        if (urine && stool) {
            return DiaperType.MIXED;
        }
        if (urine) {
            return DiaperType.URINE;
        }
        if (stool) {
            return DiaperType.STOOL;
        }
        return DiaperType.OTHER;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidTime(int hour, int minute) {
        return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59;
    }

    private int parseInt(String value) {
        return Integer.parseInt(value);
    }
}
