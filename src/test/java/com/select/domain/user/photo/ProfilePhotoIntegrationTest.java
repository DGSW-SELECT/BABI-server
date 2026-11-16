package com.select.domain.user.photo;

import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfilePhotoIntegrationTest {
    @Autowired ProfilePhotoService photos;
    @Autowired UserRepository users;

    @Test
    void storesReplacesAndSeparatesUsersPhotos() throws Exception {
        User a = users.saveAndFlush(User.builder().email("photo-a@example.invalid").nickname("a").passwordHash("hash").build());
        User b = users.saveAndFlush(User.builder().email("photo-b@example.invalid").nickname("b").passwordHash("hash").build());
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", bytes.toByteArray());
        photos.save(a.getId(), file);
        assertNotNull(photos.get(a.getId()));
        assertNull(photos.get(b.getId()));
        photos.save(a.getId(), file);
        assertNotNull(photos.get(a.getId()));
    }

    @Test
    void rejectsNonImageAndOversizedFiles() {
        User user = users.saveAndFlush(User.builder().email("photo-invalid@example.invalid").nickname("a").passwordHash("hash").build());
        var invalid = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});
        assertEquals(ErrorCode.PROFILE_PHOTO_INVALID, assertThrows(BusinessException.class, () -> photos.save(user.getId(), invalid)).getErrorCode());
        var large = new MockMultipartFile("file", "a.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        assertEquals(ErrorCode.PROFILE_PHOTO_INVALID, assertThrows(BusinessException.class, () -> photos.save(user.getId(), large)).getErrorCode());
    }
}
