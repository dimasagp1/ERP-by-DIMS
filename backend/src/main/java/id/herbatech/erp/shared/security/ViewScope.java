package id.herbatech.erp.shared.security;

/** Cakupan data yang boleh dilihat suatu peran (PRD §2 kolom "Lihat"). Urutan = makin luas. */
public enum ViewScope {
    OWN, SECTION, DEPARTMENT, ALL;

    public ViewScope widest(ViewScope other) {
        return other != null && other.ordinal() > ordinal() ? other : this;
    }
}
