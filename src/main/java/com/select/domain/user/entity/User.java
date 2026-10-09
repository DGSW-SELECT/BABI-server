package com.select.domain.user.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,unique = true,length = 50)
    private String loginId;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false,length = 50)
    private String nickname;

    @Column(nullable = false)
    private LocalDateTime createAt;

    @Column(nullable = false)
    private LocalDateTime updateAt;

    protected User() {

    }

    @Builder
    public User(String loginId, String passwordHash, String nickname) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
    }
}
