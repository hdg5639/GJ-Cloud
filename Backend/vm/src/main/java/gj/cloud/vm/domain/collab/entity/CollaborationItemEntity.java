package gj.cloud.vm.domain.collab.entity;

import gj.cloud.vm.domain.collab.enums.CollaborationStatus;
import gj.cloud.vm.domain.collab.enums.CollaborationType;
import gj.cloud.vm.domain.collab.enums.ScopeType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("collaboration_items")
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollaborationItemEntity implements Persistable<UUID> {

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

    private CollaborationType type;
    private String tag;
    private String title;
    private String content;
    private CollaborationStatus status;
    private boolean pinned;

    @Column("created_by_id")
    private String createdById;

    @Column("created_by_email")
    private String createdByEmail;

    @Column("resolved_by_id")
    private String resolvedById;

    @Column("resolved_by_email")
    private String resolvedByEmail;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    public static CollaborationItemEntity create(ScopeType scopeType, UUID scopeId,
                                                  CollaborationType type, String tag,
                                                  String title, String content,
                                                  String createdById, String createdByEmail) {
        CollaborationStatus initStatus = type == CollaborationType.REQUEST ? CollaborationStatus.UNSOLVED : null;
        return CollaborationItemEntity.builder()
                .id(UUID.randomUUID())
                .isNew(true)
                .scopeType(scopeType)
                .scopeId(scopeId)
                .type(type)
                .tag(tag)
                .title(title)
                .content(content)
                .status(initStatus)
                .pinned(false)
                .createdById(createdById)
                .createdByEmail(createdByEmail)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public CollaborationItemEntity withUpdated(String tag, String title, String content) {
        return this.toBuilder().isNew(false).tag(tag).title(title).content(content).updatedAt(LocalDateTime.now()).build();
    }

    public CollaborationItemEntity withPinned(boolean pinned) {
        return this.toBuilder().isNew(false).pinned(pinned).build();
    }

    public CollaborationItemEntity withResolved(String resolvedById, String resolvedByEmail) {
        return this.toBuilder()
                .isNew(false)
                .status(CollaborationStatus.SOLVED)
                .resolvedById(resolvedById)
                .resolvedByEmail(resolvedByEmail)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
