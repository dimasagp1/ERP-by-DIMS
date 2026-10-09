package id.herbatech.erp.sys;

import id.herbatech.erp.shared.domain.Activatable;
import id.herbatech.erp.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** SYS-08 Mitra bisnis: supplier (pemilik PRC), customer (pemilik FIN), ekspedisi. */
@Getter
@Setter
@Entity
@Table(name = "partner", schema = "sys")
public class Partner extends BaseEntity implements Activatable {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "SUPPLIER|CUSTOMER|BOTH|EXPEDITION")
    private String type;

    private String npwp;

    private String address;

    private String city;

    private String phone;

    @Email
    private String email;

    private String currencyCode;

    @PositiveOrZero
    private int paymentTermDays = 30;

    @PositiveOrZero
    private BigDecimal creditLimit;

    private boolean active = true;
}
