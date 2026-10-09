-- Akun hutang PPh 4(2) terpisah dari PPh 23 (pemotongan atas sewa/jasa konstruksi).
INSERT INTO fin.account (code, name, type, normal_balance, parent_id)
SELECT '2108', 'Hutang PPh 4(2)', 'LIABILITY', 'C', id FROM fin.account WHERE code = '2';
