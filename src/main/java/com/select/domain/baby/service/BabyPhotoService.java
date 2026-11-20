package com.select.domain.baby.service;

import com.select.domain.baby.entity.BabyPhoto;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyPhotoRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import com.select.global.image.PhotoEncoder;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BabyPhotoService {
    private final BabyMemberRepository members;
    private final BabyRepository babies;
    private final BabyPhotoRepository photos;
    private final UserRepository users;
    private final PhotoEncoder encoder;
    private final TransactionTemplate transaction;

    public BabyPhotoService(BabyMemberRepository members, BabyRepository babies, BabyPhotoRepository photos,
            UserRepository users, PhotoEncoder encoder, PlatformTransactionManager manager) {
        this.members = members; this.babies = babies; this.photos = photos;
        this.users = users; this.encoder = encoder; this.transaction = new TransactionTemplate(manager);
        this.transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public void save(long babyId, long userId, MultipartFile file) {
        requireOwner(babyId, userId);
        byte[] png = encoder.encode(file);
        transaction.executeWithoutResult(status -> {
            lock(babyId);
            requireOwner(babyId, userId);
            var photo = photos.findById(babyId).orElseGet(() -> new BabyPhoto(babyId, png));
            photo.replace(png);
            photos.save(photo);
        });
    }

    public void delete(long babyId, long userId) {
        requireOwner(babyId, userId);
        transaction.executeWithoutResult(status -> {
            lock(babyId); requireOwner(babyId, userId);
            photos.findById(babyId).ifPresent(photos::delete);
        });
    }

    @Transactional(readOnly = true)
    public byte[] get(long babyId, long userId) {
        requireActive(userId);
        if (!members.existsByBabyIdAndUserId(babyId, userId)) throw new BusinessException(ErrorCode.BABY_MEMBER_NOT_FOUND);
        return photos.findById(babyId).map(BabyPhoto::getImageData).orElse(null);
    }

    private void requireActive(long userId) {
        users.findById(userId).filter(User::isActive).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
    private void requireOwner(long babyId, long userId) {
        requireActive(userId);
        if (!members.existsByBabyIdAndUserIdAndRole(babyId, userId, BabyRole.OWNER))
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
    private void lock(long babyId) {
        babies.findByIdForUpdate(babyId).orElseThrow(() -> new BusinessException(ErrorCode.BABY_MEMBER_NOT_FOUND));
    }
}
