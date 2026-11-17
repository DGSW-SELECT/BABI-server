package com.select.domain.baby.entity;

import java.io.Serializable;
import java.util.Objects;

public class BabyPermissionId implements Serializable {
    private Long babyMemberId;
    private String permission;

    public BabyPermissionId() {}

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BabyPermissionId that)) return false;
        return Objects.equals(babyMemberId, that.babyMemberId) && Objects.equals(permission, that.permission);
    }

    @Override
    public int hashCode() {
        return Objects.hash(babyMemberId, permission);
    }
}
