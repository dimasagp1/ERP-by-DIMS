package id.herbatech.erp.shared.web;

import id.herbatech.erp.shared.error.BusinessException;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Filter generik dari query string: {@code ?q=teks&type=RM&active=true&sort=-code&page=0&size=50}.
 * Hanya atribut yang benar-benar ada di entitas yang dipakai, sehingga aman dari injeksi.
 */
public final class Specs {

    public static final Set<String> RESERVED = Set.of("q", "page", "size", "sort", "from", "to");
    public static final int MAX_PAGE_SIZE = 10_000;

    private Specs() {
    }

    public static <E> Specification<E> fromParams(Map<String, String> params, List<String> searchFields) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            String q = params.get("q");
            if (q != null && !q.isBlank() && !searchFields.isEmpty()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                preds.add(cb.or(searchFields.stream()
                        .map(f -> cb.like(cb.lower(root.get(f).as(String.class)), like))
                        .toArray(Predicate[]::new)));
            }
            params.forEach((key, raw) -> {
                if (RESERVED.contains(key) || raw == null || raw.isBlank() || !hasAttribute(root, key)) {
                    return;
                }
                Path<Object> path = root.get(key);
                Class<?> type = path.getJavaType();
                if (raw.contains(",")) {
                    preds.add(path.in(Arrays.stream(raw.split(",")).map(v -> convert(v.trim(), type)).toList()));
                } else if ("null".equals(raw)) {
                    preds.add(cb.isNull(path));
                } else {
                    preds.add(cb.equal(path, convert(raw.trim(), type)));
                }
            });
            return cb.and(preds.toArray(Predicate[]::new));
        };
    }

    public static Pageable pageable(Map<String, String> params, Sort defaultSort, Class<?> entity) {
        int page = parseInt(params.get("page"), 0);
        int size = Math.min(Math.max(parseInt(params.get("size"), 50), 1), MAX_PAGE_SIZE);
        Sort sort = defaultSort;
        String s = params.get("sort");
        if (s != null && !s.isBlank()) {
            boolean desc = s.startsWith("-");
            String field = desc ? s.substring(1) : s;
            if (!hasField(entity, field)) {
                throw new BusinessException("SORT", "Kolom urut tidak dikenal: " + field);
            }
            sort = desc ? Sort.by(field).descending() : Sort.by(field).ascending();
        }
        return PageRequest.of(Math.max(page, 0), size, sort.and(Sort.by("id")));
    }

    private static boolean hasAttribute(Root<?> root, String name) {
        try {
            root.getModel().getAttribute(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean hasField(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                c.getDeclaredField(name);
                return true;
            } catch (NoSuchFieldException ignored) {
                // cek superclass
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object convert(String v, Class<?> type) {
        try {
            if (type == String.class) return v;
            if (type == Long.class || type == long.class) return Long.valueOf(v);
            if (type == Integer.class || type == int.class) return Integer.valueOf(v);
            if (type == Short.class || type == short.class) return Short.valueOf(v);
            if (type == Boolean.class || type == boolean.class) return Boolean.valueOf(v);
            if (type == BigDecimal.class) return new BigDecimal(v);
            if (type == LocalDate.class) return LocalDate.parse(v);
            if (type.isEnum()) return Enum.valueOf((Class<Enum>) type, v);
        } catch (RuntimeException e) {
            throw new BusinessException("FILTER", "Nilai filter tidak valid: " + v);
        }
        return v;
    }

    private static int parseInt(String v, int def) {
        try {
            return v == null ? def : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
