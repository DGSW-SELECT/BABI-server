package com.select.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_profile_photos")
public class ProfilePhoto {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Lob
    @Column(name = "image_data", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] imageData;

    protected ProfilePhoto() {}

    public ProfilePhoto(Long userId, byte[] imageData) {
        this.userId = userId;
        this.imageData = imageData;
    }

    public byte[] getImageData() { return imageData; }
    public void replace(byte[] imageData) { this.imageData = imageData; }
}
