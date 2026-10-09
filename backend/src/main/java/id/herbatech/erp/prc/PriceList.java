package id.herbatech.erp.prc;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** PRC-08 Kontrak & daftar harga per supplier × item, dipakai otomatis saat PO. */
@Getter
@Setter
@Entity
@Table(name = "price_list", schema = "prc")
public class PriceList extends BaseEntity implements Activatable {

    @NotNull
    private Long partnerId;

    @NotNull
    private Long itemId;

    private String contractNo;

    @NotNull
    private BigDecimal price;

    @NotBlank
    private String currencyCode = "IDR";

    @NotNull
    private BigDecimal minQty = BigDecimal.ZERO;

    private int leadTimeDays = 14;

    @NotNull
    private LocalDate validFrom;

    private LocalDate validUntil;

    private boolean active = true;
}
