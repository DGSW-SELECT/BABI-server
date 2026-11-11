package com.select.domain.growth.dto.response;

import com.select.domain.baby.entity.BabySex;
import java.math.BigDecimal;

/** Reference comparison, not a diagnosis. Null percentiles indicate no measurement. */
public record GrowthStandardResponse(String status, String source, String sourceUrl, BabySex sex,
                                     Long ageMonths, BigDecimal heightPercentile, BigDecimal weightPercentile) { }
