-- =====================================================================
-- Data referensi M3 (QMS, RND, dll)
-- =====================================================================

-- ---------- Jenis dokumen M3 (SYS-05) ----------
INSERT INTO sys.doc_type (code, name, app_code, menu_code, prefix, requires_esign) VALUES
 ('QMSDOC', 'Dokumen Mutu & SOP', 'QMS', 'QMS-01', 'DOC', TRUE),
 ('CMP',    'Keluhan & Recall',   'QMS', 'QMS-05', 'CMP', TRUE),
 ('STB',    'Uji Stabilitas',     'QMS', 'QMS-15', 'STB', FALSE);

-- ---------- Parameter QMS (QMS-99) ----------
-- (Bisa ditambahkan jika perlu)
