package com.select.domain.baby.request;

import com.fasterxml.jackson.annotation.JsonCreator;
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
        LocalDate birthDate
) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static BabyUpdateRequest from(Map<String, Object> fields) {
        // A record constructor alone cannot distinguish omitted fields from explicit JSON null.
        if ((fields.containsKey("name") && fields.get("name") == null)
                || (fields.containsKey("birthDate") && fields.get("birthDate") == null)) {
            throw new IllegalArgumentException("수정할 필드는 null일 수 없습니다.");
        }
        String name = (String) fields.get("name");
        String birthDate = (String) fields.get("birthDate");
        return new BabyUpdateRequest(name, birthDate == null ? null : LocalDate.parse(birthDate));
    }
}
