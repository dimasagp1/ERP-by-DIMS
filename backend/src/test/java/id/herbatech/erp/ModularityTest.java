package id.herbatech.erp;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Menjaga batas modul: modul departemen hanya boleh bergantung pada shared kernel, tanpa siklus. */
class ModularityTest {

    @Test
    void modulesRespectBoundaries() {
        ApplicationModules.of(ErpBackendApplication.class).verify();
    }
}
