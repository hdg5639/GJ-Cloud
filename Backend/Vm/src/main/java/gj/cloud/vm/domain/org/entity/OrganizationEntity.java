package gj.cloud.vm.domain.org.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("organizations")
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizationEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    @Builder.Default
    private boolean isNew = false;

    @Override
    public boolean isNew() { return isNew; }

    private String name;

    @Column("owner_id")
    private String ownerId;

    @Column("created_at")
    private LocalDateTime createdAt;

    public static OrganizationEntity create(String name, String ownerId) {
        return OrganizationEntity.builder()
                .id(UUID.randomUUID())
                .isNew(true)
                .name(name)
                .ownerId(ownerId)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public OrganizationEntity withName(String name) {
        return this.toBuilder().isNew(false).name(name).build();
    }
}
