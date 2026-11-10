package com.select.domain.growth.standard;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabySex;
import com.select.domain.growth.entity.GrowthRecord;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class GrowthStandardServiceTest {
    private final GrowthStandardService service = new GrowthStandardService();
    private Baby baby(BabySex sex) {
        return Baby.builder().name("baby").birthDate(LocalDate.of(2025,1,1)).createdByUserId(1L).sex(sex).build();
    }
    private GrowthRecord record(Baby b, LocalDate date, String height, String weight) {
        return new GrowthRecord(b, 1L, height == null ? null : new BigDecimal(height),
                weight == null ? null : new BigDecimal(weight), date, null);
    }
    @Test void matchesOfficialCalculatorForMaleAndFemale() {
        var male = baby(BabySex.MALE);
        var m = service.assess(male, record(male, LocalDate.of(2026,1,1), "75", "9"));
        assertEquals("AVAILABLE", m.status());
        assertEquals(12L, m.ageMonths());
        assertEquals(new BigDecimal("37.6"), m.heightPercentile());
        assertEquals(new BigDecimal("26.3"), m.weightPercentile());
        var female = baby(BabySex.FEMALE);
        var f = service.assess(female, record(female, LocalDate.of(2026,1,1), "75", "9"));
        assertEquals(new BigDecimal("64.9"), f.heightPercentile());
        assertEquals(new BigDecimal("51.9"), f.weightPercentile());
    }
    @Test void handlesMissingSexMissingMeasurementAndBounds() {
        var unspecified = baby(null);
        assertEquals("SEX_REQUIRED", service.assess(unspecified, record(unspecified, LocalDate.of(2026,1,1), "75", null)).status());
        var b = baby(BabySex.MALE);
        var single = service.assess(b, record(b, LocalDate.of(2026,1,1), "75", null));
        assertNotNull(single.heightPercentile()); assertNull(single.weightPercentile());
        assertEquals("MEASURED_BEFORE_BIRTH", service.assess(b, record(b, LocalDate.of(2024,12,31), "75", "9")).status());
        assertEquals("AVAILABLE", service.assess(b, record(b, LocalDate.of(2043,12,1), "75", "9")).status());
        assertEquals("AGE_OUT_OF_RANGE", service.assess(b, record(b, LocalDate.of(2044,1,1), "75", "9")).status());
    }
}
