package com.select.domain.voice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "voice_inputs",
        indexes = @Index(name = "idx_voice_inputs_baby_id", columnList = "baby_id")
)

public class VoiceInput {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "baby_id", nullable = false)
    private Long babyId;

    @Column(name = "family_member_id", nullable = false)
    private Long familyMemberId;

    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    @Column(name = "transcript", columnDefinition = "TEXT")
    private String transcript;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 20)
    private SttStatus sttStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected VoiceInput() {
    }

    @Builder
    public VoiceInput(
            Long babyId,
            Long familyMemberId,
            String audioUrl,
            String transcript,
            SttStatus sttStatus
    ) {
        this.babyId = babyId;
        this.familyMemberId = familyMemberId;
        this.audioUrl = audioUrl;
        this.transcript = transcript;
        this.sttStatus = sttStatus;
    }

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
