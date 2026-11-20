package com.select.domain.user.service;

import com.select.domain.user.entity.ProfilePhoto;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.ProfilePhotoRepository;
import com.select.domain.user.repository.UserRepository;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import com.select.global.image.PhotoEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfilePhotoService {
    private final PhotoEncoder encoder;
    private final ProfilePhotoRepository photos;
    private final UserRepository users;

    public ProfilePhotoService(ProfilePhotoRepository photos, UserRepository users, PhotoEncoder encoder) {
        this.photos = photos;
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public void save(long userId, MultipartFile file) {
        byte[] png = encoder.encode(file);
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

}
