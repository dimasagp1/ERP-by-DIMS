package id.herbatech.erp.shared.error;

import lombok.Getter;

/** Pelanggaran aturan main bisnis. Pesan ditampilkan apa adanya ke pengguna (Bahasa Indonesia). */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static BusinessException of(String code, String message, Object... args) {
        return new BusinessException(code, args.length == 0 ? message : String.format(message, args));
    }
}
