package com.stackcraft.retroflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(
    name = "action_item",
    indexes = {
        @Index(name = "ix_action_item_priority", columnList = "priority"),
        @Index(name = "ix_action_item_completed", columnList = "completed")
    }
)
@DiscriminatorValue("ACTION_ITEM")
public class ActionItem extends FeedbackItem {

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ActionPriority priority;

    @Column(nullable = false)
    private boolean completed = false;

    public ActionItem() {
        super();
        super.setType(FeedbackType.ACTION_ITEM);
    }

    public ActionItem(Long id, String content, FeedbackType type,
                      String submittedBy, Retrospective retrospective,
                      ActionPriority priority, boolean completed) {
        super(id, content, requireActionItemType(type), submittedBy, retrospective);
        this.priority = priority;
        this.completed = completed;
    }

    private static FeedbackType requireActionItemType(FeedbackType type) {
        if (type != FeedbackType.ACTION_ITEM) {
            throw new IllegalArgumentException(
                "ActionItem type must be ACTION_ITEM"
            );
        }
        return type;
    }

    @Override
    public void setType(FeedbackType type) {
        if (type != FeedbackType.ACTION_ITEM) {
            throw new IllegalArgumentException(
                "ActionItem type must be ACTION_ITEM"
            );
        }
        super.setType(type);
    }

    public ActionPriority getPriority() {
        return priority;
    }

    public void setPriority(ActionPriority priority) {
        this.priority = priority;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}
