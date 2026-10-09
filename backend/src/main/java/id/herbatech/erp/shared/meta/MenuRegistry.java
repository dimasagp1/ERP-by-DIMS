package id.herbatech.erp.shared.meta;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry aplikasi & menu (PRD §2–§11, sama dengan prototipe UI). Kode menu adalah kunci hak akses
 * dan navigasi; satu sumber untuk backend dan frontend.
 */
@Component
public class MenuRegistry {

    public record MenuItem(String code, String name, String fn, List<String> links, String phase) {
    }

    public record MenuGroup(String name, List<MenuItem> items) {
    }

    public record DocDef(String prefix, String name) {
    }

    public record AppDef(String code, String name, String shortName, String color, boolean self, List<String> masters,
                         List<DocDef> docs, Map<String, String> sends, List<MenuGroup> groups) {
    }

    private final List<AppDef> apps;
    private final Map<String, MenuItem> byCode = new LinkedHashMap<>();
    private final Map<String, String> groupOf = new LinkedHashMap<>();

    public MenuRegistry(ObjectMapper mapper) {
        try (InputStream in = new ClassPathResource("meta/apps.json").getInputStream()) {
            List<Map<String, Object>> raw = mapper.readValue(in, new TypeReference<>() {
            });
            this.apps = raw.stream().map(m -> {
                Map<String, Object> copy = new LinkedHashMap<>(m);
                copy.put("shortName", copy.remove("short"));
                return mapper.convertValue(copy, AppDef.class);
            }).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Gagal membaca meta/apps.json", e);
        }
        apps.forEach(a -> a.groups().forEach(g -> g.items().forEach(i -> {
            byCode.put(i.code(), i);
            groupOf.put(i.code(), g.name());
        })));
    }

    public List<AppDef> apps() {
        return apps;
    }

    public MenuItem menu(String code) {
        return byCode.get(code);
    }

    public String groupOf(String code) {
        return groupOf.get(code);
    }

    public Map<String, MenuItem> allMenus() {
        return byCode;
    }
}
