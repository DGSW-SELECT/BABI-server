package com.select.domain.growth.standard;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabySex;
import com.select.domain.growth.dto.response.GrowthStandardResponse;
import com.select.domain.growth.entity.GrowthRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Service
public class GrowthStandardService {
    static final String SOURCE = "2017 소아청소년 성장도표";
    static final String URL = "https://knhanes.kdca.go.kr/knhanes/grtcht/dwnld/dtDtl.do?pg=1&st=&sb=1&menuId=20011002&dataNo=7";
    private final Map<Key, Lms> data;

    public GrowthStandardService() {
        Map<Key, Lms> loaded = new HashMap<>();
        try (var in = new BufferedReader(new InputStreamReader(new ClassPathResource("growth/kdca-2017-lms.csv").getInputStream(), StandardCharsets.UTF_8))) {
            if (!"metric,sex,age_months,L,M,S".equals(in.readLine())) throw new IllegalStateException("Invalid growth reference header");
            String line;
            while ((line = in.readLine()) != null) {
                var v = line.split(",", -1);
                if (v.length != 6) throw new IllegalStateException("Invalid growth reference row");
                var key = new Key(Metric.valueOf(v[0]), BabySex.valueOf(v[1]), Integer.parseInt(v[2]));
                var lms = new Lms(Double.parseDouble(v[3]), Double.parseDouble(v[4]), Double.parseDouble(v[5]));
                if (key.month < 0 || key.month > 227 || !Double.isFinite(lms.l) || !Double.isFinite(lms.m) || !Double.isFinite(lms.s)
                        || lms.m <= 0 || lms.s <= 0 || loaded.putIfAbsent(key, lms) != null) throw new IllegalStateException("Invalid growth reference values");
            }
            if (loaded.size() != 912) throw new IllegalStateException("Incomplete growth reference");
        } catch (IOException | IllegalArgumentException e) { throw new IllegalStateException("Cannot load growth reference", e); }
        data = Map.copyOf(loaded);
    }

    public GrowthStandardResponse assess(Baby baby, GrowthRecord record) {
        BabySex sex = baby.getSex();
        if (sex == null) return response("SEX_REQUIRED", null, null, null, null);
        if (record.getMeasuredDate().isBefore(baby.getBirthDate())) return response("MEASURED_BEFORE_BIRTH", sex, null, null, null);
        long months = ChronoUnit.MONTHS.between(baby.getBirthDate(), record.getMeasuredDate());
        if (months > 227) return response("AGE_OUT_OF_RANGE", sex, months, null, null);
        return response("AVAILABLE", sex, months,
                percentile(record.getHeight(), data.get(new Key(Metric.HEIGHT, sex, (int) months))),
                percentile(record.getWeight(), data.get(new Key(Metric.WEIGHT, sex, (int) months))));
    }

    private static GrowthStandardResponse response(String status, BabySex sex, Long months, BigDecimal height, BigDecimal weight) {
        return new GrowthStandardResponse(status, SOURCE, URL, sex, months, height, weight);
    }

    private static BigDecimal percentile(BigDecimal measurement, Lms lms) {
        if (measurement == null) return null;
        double x = measurement.doubleValue();
        if (!(x > 0) || !Double.isFinite(x)) throw new IllegalStateException("Invalid growth measurement");
        double z = lms.l == 0 ? Math.log(x / lms.m) / lms.s : Math.expm1(lms.l * Math.log(x / lms.m)) / (lms.l * lms.s);
        return BigDecimal.valueOf(normalCdf(z) * 100).setScale(1, RoundingMode.HALF_UP);
    }

    // Normal CDF approximation, max absolute error below 7.5e-8.
    private static double normalCdf(double z) {
        if (z == 0) return 0.5;
        double x = Math.abs(z);
        double t = 1.0 / (1.0 + 0.2316419 * x);
        double tail = Math.exp(-0.5 * x * x) / Math.sqrt(2 * Math.PI) * t
                * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))));
        return z > 0 ? 1.0 - tail : tail;
    }
    private enum Metric { HEIGHT, WEIGHT }
    private record Key(Metric metric, BabySex sex, int month) { }
    private record Lms(double l, double m, double s) { }
}
