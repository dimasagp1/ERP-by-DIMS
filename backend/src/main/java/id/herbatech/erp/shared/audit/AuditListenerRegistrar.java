package id.herbatech.erp.shared.audit;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.springframework.context.annotation.Configuration;

/** Mendaftarkan {@link AuditEventListener} ke Hibernate saat aplikasi mulai. */
@Configuration
public class AuditListenerRegistrar {

    public AuditListenerRegistrar(EntityManagerFactory emf, AuditEventListener listener) {
        EventListenerRegistry registry = emf.unwrap(SessionFactoryImplementor.class).getEventListenerRegistry();
        registry.appendListeners(EventType.POST_INSERT, listener);
        registry.appendListeners(EventType.POST_UPDATE, listener);
        registry.appendListeners(EventType.POST_DELETE, listener);
    }
}
