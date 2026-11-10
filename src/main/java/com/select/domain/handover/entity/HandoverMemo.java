package com.select.domain.handover.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "handover_memos")
public class HandoverMemo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "baby_id", nullable = false)
    private Long babyId;

    @Column(name = "author_member_id", nullable = false)
    private Long authorMemberId;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected HandoverMemo() {
    }

    public HandoverMemo(Long babyId, Long authorMemberId, String content, LocalDateTime createdAt) {
        this.babyId = babyId;
        this.authorMemberId = authorMemberId;
        this.content = content;
        this.createdAt = createdAt;
    }
}
