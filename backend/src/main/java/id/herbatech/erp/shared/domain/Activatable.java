package id.herbatech.erp.shared.domain;

/** Master data yang dinonaktifkan, bukan dihapus, agar referensi historis tetap utuh. */
public interface Activatable {

    boolean isActive();

    void setActive(boolean active);
}
