package com.project.Splitwise.readmodel;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

@NoArgsConstructor
@AllArgsConstructor
public class GroupBalanceViewId implements Serializable {

    private Long groupId;
    private Long userId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if(!(o instanceof GroupBalanceViewId)) return false;
        GroupBalanceViewId that = (GroupBalanceViewId) o;
        return this.groupId.equals(that.groupId) && this.userId.equals(that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, userId);
    }
}
