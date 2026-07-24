package gj.cloud.vm.domain.port.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("vm_ssh_access_emails")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VmSshAccessEmailEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    @Builder.Default
    private boolean isNew = false;

    @Override
    public boolean isNew() { return isNew; }

    @Column("vm_id")
    private UUID vmId;

    private String email;

    public static VmSshAccessEmailEntity create(UUID vmId, String email) {
        return VmSshAccessEmailEntity.builder()
                .id(UUID.randomUUID()).isNew(true)
                .vmId(vmId).email(email).build();
    }
}
