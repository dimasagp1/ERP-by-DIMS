package id.herbatech.erp.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Konfigurasi aplikasi di bawah prefix {@code erp.*}. */
@ConfigurationProperties(prefix = "erp")
public record ErpProperties(String timezone, Security security, Files files, Demo demo) {

    public record Security(String jwtSecret, int tokenTtlHours) {
    }

    public record Files(String dir) {
    }

    public record Demo(boolean seedUsers, String password) {
    }

    public ErpProperties {
        if (timezone == null) {
            timezone = "Asia/Jakarta";
        }
        if (demo == null) {
            demo = new Demo(false, null);
        }
        if (files == null) {
            files = new Files("./data/files");
        }
    }
}
