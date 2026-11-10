package com.select.domain.baby.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.select.domain.baby.entity.BabySex;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Map;

public record BabyUpdateRequest(
        @Size(max = 50)
        @Pattern(regexp = "(?s).*[^\\p{javaWhitespace}].*", message = "이름은 공백일 수 없습니다.")
        String name,

        @PastOrPresent
        LocalDate birthDate,
        BabySex sex
) {
    public BabyUpdateRequest(String name, LocalDate birthDate) { this(name, birthDate, null); }
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static BabyUpdateRequest from(Map<String, Object> fields) {
        // A record constructor alone cannot distinguish omitted fields from explicit JSON null.
        if ((fields.containsKey("name") && fields.get("name") == null)
                || (fields.containsKey("birthDate") && fields.get("birthDate") == null)
                || (fields.containsKey("sex") && fields.get("sex") == null)) {
            throw new IllegalArgumentException("수정할 필드는 null일 수 없습니다.");
        }
        String name = (String) fields.get("name");
        String birthDate = (String) fields.get("birthDate");
        String sex = (String) fields.get("sex");
        return new BabyUpdateRequest(name, birthDate == null ? null : LocalDate.parse(birthDate), sex == null ? null : BabySex.valueOf(sex));
    }
}
