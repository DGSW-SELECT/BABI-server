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

    @Column(nullable = false)
    private Long groupId;

    @Column(nullable = false)
    private Long babyId;

    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected HandoverMemo() {
    }

    public HandoverMemo(
            Long groupId,
            Long babyId,
            Long authorId,
            String content,
            LocalDateTime createdAt
    ) {
        this.groupId = groupId;
        this.babyId = babyId;
        this.authorId = authorId;
        this.content = content;
        this.createdAt = createdAt;
    }
}
