package com.select.domain.user.service;

import com.select.domain.user.entity.ProfilePhoto;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.ProfilePhotoRepository;
import com.select.domain.user.repository.UserRepository;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfilePhotoService {
    private static final long MAX_BYTES = 5 * 1024 * 1024;
    private static final long MAX_PIXELS = 16_000_000;
    private final Semaphore imageEncoders = new Semaphore(2);
    private final ProfilePhotoRepository photos;
    private final UserRepository users;

    public ProfilePhotoService(ProfilePhotoRepository photos, UserRepository users) {
        this.photos = photos;
        this.users = users;
    }

    @Transactional
    public void save(long userId, MultipartFile file) {
        if (!imageEncoders.tryAcquire()) {
            throw new BusinessException(ErrorCode.PROFILE_PHOTO_BUSY);
        }
        byte[] png;
        try {
            png = validateAndEncode(file);
        } finally {
            imageEncoders.release();
        }
        users.findByIdForUpdate(userId).filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        ProfilePhoto photo = photos.findById(userId).orElseGet(() -> new ProfilePhoto(userId, png));
        photo.replace(png);
        photos.save(photo);
    }

    @Transactional(readOnly = true)
    public byte[] get(long userId) {
        users.findById(userId).filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return photos.findById(userId).map(ProfilePhoto::getImageData).orElse(null);
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
