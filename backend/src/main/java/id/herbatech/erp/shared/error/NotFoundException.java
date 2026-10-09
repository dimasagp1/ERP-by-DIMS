package id.herbatech.erp.shared.error;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, Object id) {
        super(what + " " + id + " tidak ditemukan");
    }
}
