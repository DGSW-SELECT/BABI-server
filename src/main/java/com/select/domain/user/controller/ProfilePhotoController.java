package com.select.domain.user.controller;

import com.select.domain.user.service.ProfilePhotoService;

import com.select.global.exception.ErrorCode;
import com.select.global.response.ApiError;
import com.select.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/auth/profile/photo")
public class ProfilePhotoController {
    private final ProfilePhotoService photos;

    public ProfilePhotoController(ProfilePhotoService photos) { this.photos = photos; }

    @Operation(summary = "내 프로필 사진 업로드")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Void> upload(@AuthenticationPrincipal String principal, @RequestPart("file") MultipartFile file) {
        photos.save(Long.parseLong(principal), file);
        return ApiResponse.success("프로필 사진을 저장했습니다.");
    }

    @Operation(summary = "내 프로필 사진 조회", responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "저장된 사진",
                    content = @Content(mediaType = MediaType.IMAGE_PNG_VALUE, schema = @Schema(type = "string", format = "binary"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "저장된 사진 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping(produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<?> get(@AuthenticationPrincipal String principal) {
        byte[] photo = photos.get(Long.parseLong(principal));
        if (photo == null) {
            ErrorCode error = ErrorCode.PROFILE_PHOTO_NOT_FOUND;
            return ResponseEntity.status(error.getHttpStatus()).cacheControl(CacheControl.noStore())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiResponse<>(false, error.getMessage(), null, new ApiError(error.name(), List.of())));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(photo);
    }
}
