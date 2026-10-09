/**
 * Pengaturan Sistem (SYS-01 .. SYS-15): master data bersama yang dibaca semua aplikasi.
 * Pemilik data (PRD §3) boleh mengubah lewat menu departemennya; departemen lain hanya membaca.
 */
@ApplicationModule(displayName = "Pengaturan Sistem")
package id.herbatech.erp.sys;

import org.springframework.modulith.ApplicationModule;
