package com.select.domain.user.controller;

import com.select.domain.user.service.ProfilePhotoService;

import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import com.select.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
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
        return new ApiResponse<>(true, "프로필 사진을 저장했습니다.", null);
    }

    @Operation(summary = "내 프로필 사진 조회")
    @GetMapping(produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> get(@AuthenticationPrincipal String principal) {
        byte[] photo = photos.get(Long.parseLong(principal));
        if (photo == null) throw new BusinessException(ErrorCode.PROFILE_PHOTO_NOT_FOUND);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(photo);
    }
}
