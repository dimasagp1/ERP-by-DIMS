package id.herbatech.erp.pre;

import id.herbatech.erp.shared.domain.DocumentEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ipc_entry", schema = "pre")
public class IpcEntry extends DocumentEntity {
    private Long woId;
    private String stageName;
    private String paramName;
    private String targetVal;
    private String measuredVal;
    private Boolean pass;
    private Long operatorId;
}
