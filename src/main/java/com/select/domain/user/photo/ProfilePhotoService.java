package com.select.domain.user.photo;

import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfilePhotoService {
    private static final long MAX_BYTES = 5 * 1024 * 1024;
    private static final long MAX_PIXELS = 16_000_000;
    private final JdbcTemplate jdbc;
    private final UserRepository users;

    public ProfilePhotoService(JdbcTemplate jdbc, UserRepository users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    @Transactional
    public void save(long userId, MultipartFile file) {
        byte[] png = validateAndEncode(file);
        users.findByIdForUpdate(userId).filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user_profile_photos WHERE user_id = ?", Integer.class, userId);
        if (count != null && count > 0) {
            jdbc.update("UPDATE user_profile_photos SET image_data = ? WHERE user_id = ?", png, userId);
        } else {
            jdbc.update("INSERT INTO user_profile_photos (user_id, image_data) VALUES (?, ?)", userId, png);
        }
    }

    @Transactional(readOnly = true)
    public byte[] get(long userId) {
        users.findById(userId).filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return jdbc.query("SELECT image_data FROM user_profile_photos WHERE user_id = ?",
                rs -> rs.next() ? rs.getBytes(1) : null, userId);
    }

    private byte[] validateAndEncode(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES)
            throw new BusinessException(ErrorCode.PROFILE_PHOTO_INVALID);
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            if (input == null) throw new BusinessException(ErrorCode.PROFILE_PHOTO_INVALID);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BusinessException(ErrorCode.PROFILE_PHOTO_INVALID);
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName();
                if ((!format.equalsIgnoreCase("jpeg") && !format.equalsIgnoreCase("png"))
                        || (long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS)
                    throw new BusinessException(ErrorCode.PROFILE_PHOTO_INVALID);
                BufferedImage image = reader.read(0);
                if (image == null) throw new BusinessException(ErrorCode.PROFILE_PHOTO_INVALID);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "png", output) || output.size() > MAX_BYTES)
                    throw new BusinessException(ErrorCode.PROFILE_PHOTO_INVALID);
                return output.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessException exception) throw exception;
            throw new BusinessException(ErrorCode.PROFILE_PHOTO_INVALID);
        }
    }
}
