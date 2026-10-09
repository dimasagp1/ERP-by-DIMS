package id.herbatech.erp.shared.config;

import id.herbatech.erp.shared.security.UserContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", modifyOnCreate = false)
@EnableConfigurationProperties(ErpProperties.class)
@EnableScheduling
@EnableAsync
public class PersistenceConfig {

    /** Mengisi created_by / updated_by dari pengguna yang sedang login. */
    @Bean
    AuditorAware<Long> auditorAware() {
        return () -> UserContext.currentOptional().map(u -> u.id());
    }
}
