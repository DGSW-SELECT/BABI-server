package com.select.domain.baby.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "baby_photos")
public class BabyPhoto {
    @Id
    @Column(name = "baby_id")
    private Long babyId;
    @Lob
    @Column(name = "image_data", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] imageData;
    protected BabyPhoto() {}
    public BabyPhoto(Long babyId, byte[] imageData) { this.babyId = babyId; this.imageData = imageData; }
    public byte[] getImageData() { return imageData; }
    public void replace(byte[] imageData) { this.imageData = imageData; }
}
