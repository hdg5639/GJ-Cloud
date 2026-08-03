package gj.cloud.vm.application.collab.dto;

import gj.cloud.vm.domain.collab.entity.CollaborationItemEntity;
import gj.cloud.vm.domain.collab.enums.CollaborationStatus;
import gj.cloud.vm.domain.collab.enums.CollaborationType;
import gj.cloud.vm.domain.collab.enums.ScopeType;

import java.time.LocalDateTime;
import java.util.UUID;

public record CollaborationResponse(
        UUID id,
        ScopeType scopeType,
        UUID scopeId,
        CollaborationType type,
        String tag,
        String title,
        String content,
        CollaborationStatus status,
        boolean pinned,
        String createdById,
        String createdByEmail,
        String resolvedById,
        String resolvedByEmail,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CollaborationResponse from(CollaborationItemEntity e) {
        return new CollaborationResponse(
                e.getId(), e.getScopeType(), e.getScopeId(),
                e.getType(), e.getTag(), e.getTitle(), e.getContent(),
                e.getStatus(), e.isPinned(),
                e.getCreatedById(), e.getCreatedByEmail(),
                e.getResolvedById(), e.getResolvedByEmail(),
                e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
