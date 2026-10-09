package id.herbatech.erp.shared.web;

import java.util.Map;
import java.util.Set;

/** Modul mendaftarkan sumber lookup (dropdown) miliknya. Nama tabel/kolom ditulis pengembang, bukan dari input. */
public interface LookupSource {

    record Def(String table, String codeCol, String nameCol, String extraCol, String baseWhere, Set<String> filterCols) {
        public static Def of(String table, String codeCol, String nameCol) {
            return new Def(table, codeCol, nameCol, null, "active", Set.of());
        }
    }

    Map<String, Def> lookups();
}
