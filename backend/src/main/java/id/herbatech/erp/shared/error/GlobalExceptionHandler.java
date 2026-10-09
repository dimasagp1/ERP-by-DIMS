package id.herbatech.erp.shared.error;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bentuk error seragam: {@code {code, message, fields?}}. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ApiError(String code, String message, Map<String, String> fields) {
        static ApiError of(String code, String message) {
            return new ApiError(code, message, null);
        }
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> business(BusinessException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(ApiError.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> denied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("FORBIDDEN", e.getMessage() != null ? e.getMessage() : "Anda tidak memiliki hak akses untuk aksi ini"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION", "Periksa kembali isian formulir", fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraint(ConstraintViolationException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getConstraintViolations().forEach(v -> fields.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION", "Periksa kembali isian formulir", fields));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> conflict(OptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("CONFLICT", "Data sudah diubah pengguna lain. Muat ulang lalu ulangi perubahan Anda."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(DataIntegrityViolationException e) {
        String msg = e.getMostSpecificCause().getMessage();
        log.warn("Data integrity violation: {}", msg);
        String friendly = msg != null && msg.contains("duplicate key")
                ? "Kode atau nomor sudah dipakai data lain"
                : msg != null && msg.contains("foreign key")
                ? "Data masih dipakai atau referensinya tidak valid"
                : "Data melanggar aturan integritas database";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of("INTEGRITY", friendly));
    }
}
