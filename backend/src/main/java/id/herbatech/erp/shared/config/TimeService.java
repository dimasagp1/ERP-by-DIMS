package id.herbatech.erp.shared.config;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Waktu server dalam zona WIB (PRD §17). Semua "hari ini" di aturan bisnis memakai kelas ini. */
@Component
public class TimeService {

    private final Clock clock;

    public TimeService(ErpProperties props) {
        this.clock = Clock.system(ZoneId.of(props.timezone()));
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public ZoneId zone() {
        return clock.getZone();
    }
}
