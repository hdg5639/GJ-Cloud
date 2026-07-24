package gj.cloud.vm.application.collab.dto;

import gj.cloud.vm.domain.collab.entity.CollaborationTagEntity;
import gj.cloud.vm.domain.collab.enums.ScopeType;

import java.util.UUID;

public record TagResponse(UUID id, ScopeType scopeType, UUID scopeId, String name, int usageCount) {
    public static TagResponse from(CollaborationTagEntity e) {
        return new TagResponse(e.getId(), e.getScopeType(), e.getScopeId(), e.getName(), e.getUsageCount());
    }
}
