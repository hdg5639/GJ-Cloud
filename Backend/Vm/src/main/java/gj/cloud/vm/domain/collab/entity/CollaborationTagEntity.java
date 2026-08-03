package gj.cloud.vm.domain.collab.entity;

import gj.cloud.vm.domain.collab.enums.ScopeType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("collaboration_tags")
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollaborationTagEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    @Builder.Default
    private boolean isNew = false;

    @Override
    public boolean isNew() { return isNew; }

    @Column("scope_type")
    private ScopeType scopeType;

    @Column("scope_id")
    private UUID scopeId;

    private String name;

    @Column("usage_count")
    private int usageCount;

    @Column("last_used_at")
    private LocalDateTime lastUsedAt;

    @Column("created_by")
    private String createdBy;

    @Column("created_at")
    private LocalDateTime createdAt;

    public static CollaborationTagEntity create(ScopeType scopeType, UUID scopeId, String name, String createdBy) {
        return CollaborationTagEntity.builder()
                .id(UUID.randomUUID())
                .isNew(true)
                .scopeType(scopeType)
                .scopeId(scopeId)
                .name(name)
                .usageCount(1)
                .lastUsedAt(LocalDateTime.now())
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public CollaborationTagEntity withIncrementedUsage() {
        return this.toBuilder().isNew(false).usageCount(usageCount + 1).lastUsedAt(LocalDateTime.now()).build();
    }
}
