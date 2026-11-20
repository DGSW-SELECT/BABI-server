package com.select.domain.baby.controller;

import com.select.domain.baby.service.BabyPhotoService;
import com.select.global.response.ApiError;
import com.select.global.exception.ErrorCode;
import com.select.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/babies/{babyId}/photo")
public class BabyPhotoController {
    private final BabyPhotoService service;
    public BabyPhotoController(BabyPhotoService service) { this.service = service; }

    @Operation(summary = "아기 사진 등록·교체", description = "OWNER만 가능. JPEG/PNG 5MiB 이하, 1600만 픽셀 이하. PNG로 저장합니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Void> save(@PathVariable long babyId, @AuthenticationPrincipal String userId,
            @RequestPart("file") MultipartFile file) {
        service.save(babyId, Long.parseLong(userId), file);
        return ApiResponse.success("아기 사진을 저장했습니다.");
    }

    @Operation(summary = "아기 사진 조회", description = "현재 보호자만 가능. PNG 또는 사진이 없으면 404 JSON.", responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "저장된 아기 사진",
                    content = @Content(mediaType = MediaType.IMAGE_PNG_VALUE, schema = @Schema(type = "string", format = "binary"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사진 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping
    public ResponseEntity<?> get(@PathVariable long babyId, @AuthenticationPrincipal String userId) {
        byte[] png = service.get(babyId, Long.parseLong(userId));
        if (png == null) {
            var error = ErrorCode.BABY_PHOTO_NOT_FOUND;
            return ResponseEntity.status(error.getHttpStatus()).cacheControl(CacheControl.noStore())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiResponse<>(false, error.getMessage(), null, new ApiError(error.name(), List.of())));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(png);
    }

    @Operation(summary = "아기 사진 삭제", description = "OWNER만 가능. 사진이 없어도 성공합니다.")
    @DeleteMapping
    public ApiResponse<Void> delete(@PathVariable long babyId, @AuthenticationPrincipal String userId) {
        service.delete(babyId, Long.parseLong(userId));
        return ApiResponse.success("아기 사진을 삭제했습니다.");
    }
}
