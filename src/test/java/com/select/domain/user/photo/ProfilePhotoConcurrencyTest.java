package com.select.domain.user.photo;

import com.select.domain.user.repository.ProfilePhotoRepository;
import com.select.domain.user.repository.UserRepository;
import com.select.domain.user.service.ProfilePhotoService;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfilePhotoConcurrencyTest {
    @Test
    void rejectsAnOverlappingUploadAndReleasesPermitAfterFailure() throws Exception {
        var service = new ProfilePhotoService(mock(ProfilePhotoRepository.class), mock(UserRepository.class), new com.select.global.image.PhotoEncoder());
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        MultipartFile blockingFile = mock(MultipartFile.class);
        when(blockingFile.getBytes()).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            throw new IOException("test file read failure");
        });
        MultipartFile empty = mock(MultipartFile.class);
        when(empty.isEmpty()).thenReturn(true);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> uploadError(service, blockingFile));
            var second = executor.submit(() -> uploadError(service, blockingFile));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertEquals(ErrorCode.PROFILE_PHOTO_BUSY, uploadError(service, empty));
            } finally {
                release.countDown();
            }
            assertEquals(ErrorCode.PROFILE_PHOTO_INVALID, first.get(5, TimeUnit.SECONDS));
            assertEquals(ErrorCode.PROFILE_PHOTO_INVALID, second.get(5, TimeUnit.SECONDS));
        }
        assertEquals(ErrorCode.PROFILE_PHOTO_INVALID, uploadError(service, empty));
    }

    private ErrorCode uploadError(ProfilePhotoService service, MultipartFile file) {
        return assertThrows(BusinessException.class, () -> service.save(1L, file)).getErrorCode();
    }
}
