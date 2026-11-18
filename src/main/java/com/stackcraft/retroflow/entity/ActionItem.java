package com.stackcraft.retroflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

/**
 * Feedback item with action-item-specific priority and completion state.
 */
@Entity
@Table(name = "action_items")
@DiscriminatorValue("ACTION_ITEM")
@PrimaryKeyJoinColumn(name = "feedback_item_id")
public class ActionItem extends FeedbackItem {

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionItemPriority priority;

    @Column(nullable = false)
    private boolean completed;

    public ActionItem() {
        setType(FeedbackItemType.ACTION_ITEM);
    }

    @Override
    public void setType(FeedbackItemType type) {
        if (type != FeedbackItemType.ACTION_ITEM) {
            throw new IllegalArgumentException("An action item must have type ACTION_ITEM.");
        }
        super.setType(type);
    }

    public ActionItemPriority getPriority() {
        return priority;
    }

    public void setPriority(ActionItemPriority priority) {
        this.priority = priority;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

}
