-- =====================================================================
-- V016: Skema M3 - RND, GA, PRE (eBMR & Maintenance), HC Lanjutan
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS rnd;
CREATE SCHEMA IF NOT EXISTS ga;

-- =====================================================================
-- 1. RND (Research & Development)
-- =====================================================================

-- RND-02 Proyek Pengembangan Produk
CREATE TABLE rnd.project (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  name VARCHAR(255) NOT NULL,
  category VARCHAR(100),
  stage VARCHAR(50) DEFAULT 'IDEA',
  pic_id BIGINT,
  target_launch_date DATE,
  description TEXT
);

-- RND-03 Master Formula & Versi
CREATE TABLE rnd.formula (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  product_item_id BIGINT NOT NULL,
  formula_version VARCHAR(20) NOT NULL,
  batch_size NUMERIC(19,4) NOT NULL,
  uom_id BIGINT,
  target_yield_pct NUMERIC(5,2) DEFAULT 100.00,
  notes TEXT
);

CREATE TABLE rnd.formula_line (
  id BIGSERIAL PRIMARY KEY,
  formula_id BIGINT NOT NULL REFERENCES rnd.formula(id) ON DELETE CASCADE,
  item_id BIGINT NOT NULL,
  qty NUMERIC(19,4) NOT NULL,
  uom_id BIGINT,
  phase_name VARCHAR(100),
  substitute_item_id BIGINT,
  tolerance_pct NUMERIC(5,2) DEFAULT 0
);

-- RND-06 Trial Lab & Scale-up
CREATE TABLE rnd.trial (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  project_id BIGINT,
  formula_id BIGINT,
  trial_type VARCHAR(50),
  trial_date DATE,
  parameters_json TEXT,
  result_summary TEXT,
  decision VARCHAR(50)
);

-- RND-07 Draf Spesifikasi
CREATE TABLE rnd.product_spec (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  item_id BIGINT NOT NULL,
  spec_category VARCHAR(50),
  parameters_json TEXT,
  notes TEXT
);

-- RND-09 Registrasi Produk
CREATE TABLE rnd.product_registration (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  item_id BIGINT NOT NULL,
  reg_type VARCHAR(50),
  registration_no VARCHAR(100),
  halal_no VARCHAR(100),
  submission_date DATE,
  approval_date DATE,
  expiry_date DATE
);

-- RND-10 Desain Kemasan & Artwork
CREATE TABLE rnd.artwork (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  item_id BIGINT NOT NULL,
  artwork_code VARCHAR(100) NOT NULL,
  packaging_version VARCHAR(20),
  file_url VARCHAR(255),
  approval_qa BOOLEAN DEFAULT FALSE,
  approval_marketing BOOLEAN DEFAULT FALSE
);

-- RND-12 Estimasi Biaya Formula
CREATE TABLE rnd.cost_estimate (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  formula_id BIGINT NOT NULL,
  material_cost NUMERIC(19,2),
  overhead_cost NUMERIC(19,2),
  packaging_cost NUMERIC(19,2),
  total_cost NUMERIC(19,2),
  target_margin_pct NUMERIC(5,2),
  suggested_price NUMERIC(19,2)
);


-- =====================================================================
-- 2. GA (General Affairs)
-- =====================================================================

-- GA-02 Inventaris & Aset Non-Produksi
CREATE TABLE ga.inventory_asset (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  asset_code VARCHAR(50) UNIQUE NOT NULL,
  name VARCHAR(255) NOT NULL,
  category VARCHAR(50),
  employee_id BIGINT,
  location VARCHAR(100),
  condition VARCHAR(50) DEFAULT 'GOOD',
  serial_number VARCHAR(100),
  purchase_date DATE
);

-- GA-03 / GA-06 / GA-07 Permintaan Layanan GA (ATK, Fasilitas, Limbah)
CREATE TABLE ga.service_request (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  req_type VARCHAR(50) NOT NULL, -- ATK, FACILITY, CLEANING, WASTE
  requester_id BIGINT NOT NULL,
  priority VARCHAR(20) DEFAULT 'NORMAL',
  description TEXT NOT NULL,
  sla_due_date DATE,
  resolution_notes TEXT
);

-- GA-04 Kendaraan Operasional & Booking
CREATE TABLE ga.vehicle_booking (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  vehicle_name VARCHAR(100) NOT NULL,
  license_plate VARCHAR(30),
  requester_id BIGINT NOT NULL,
  driver_name VARCHAR(100),
  start_time TIMESTAMPTZ NOT NULL,
  end_time TIMESTAMPTZ NOT NULL,
  destination VARCHAR(255) NOT NULL,
  purpose VARCHAR(255)
);

-- GA-05 Booking Ruang Rapat
CREATE TABLE ga.room_booking (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  room_name VARCHAR(100) NOT NULL,
  booked_by BIGINT NOT NULL,
  start_time TIMESTAMPTZ NOT NULL,
  end_time TIMESTAMPTZ NOT NULL,
  agenda VARCHAR(255) NOT NULL,
  status VARCHAR(20) DEFAULT 'CONFIRMED'
);

-- GA-08 Gate Pass & Buku Tamu
CREATE TABLE ga.gate_pass (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  pass_type VARCHAR(50) NOT NULL, -- VISITOR, CONTRACTOR, GOODS_OUT
  person_name VARCHAR(100) NOT NULL,
  company_name VARCHAR(100),
  vehicle_plate VARCHAR(30),
  goods_description TEXT,
  delivery_doc_no VARCHAR(50),
  entry_time TIMESTAMPTZ,
  exit_time TIMESTAMPTZ
);

-- GA-09 Perizinan & Dokumen Perusahaan
CREATE TABLE ga.company_permit (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  name VARCHAR(255) NOT NULL,
  permit_no VARCHAR(100) NOT NULL,
  issuer VARCHAR(255),
  issue_date DATE,
  expiry_date DATE NOT NULL,
  reminder_days INT DEFAULT 30,
  status VARCHAR(20) DEFAULT 'ACTIVE'
);

-- GA-13 K3 & Lingkungan
CREATE TABLE ga.hsse_incident (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  incident_date TIMESTAMPTZ NOT NULL,
  location VARCHAR(100) NOT NULL,
  severity VARCHAR(30) NOT NULL, -- NEAR_MISS, MINOR, MAJOR, FATAL
  description TEXT NOT NULL,
  impact_product_lot VARCHAR(100),
  corrective_action TEXT
);


-- =====================================================================
-- 3. PRE (Produksi & Engineering Phase 3: eBMR, Engineering, Maintenance)
-- =====================================================================

-- PRE-03 Batch Record Elektronik (eBMR)
CREATE TABLE pre.batch_record (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  wo_id BIGINT NOT NULL,
  batch_no VARCHAR(50) NOT NULL,
  stage_name VARCHAR(100) NOT NULL,
  operator_id BIGINT,
  checker_id BIGINT,
  start_time TIMESTAMPTZ,
  end_time TIMESTAMPTZ,
  notes TEXT,
  esign_verified BOOLEAN DEFAULT FALSE
);

-- PRE-05 Penimbangan & Dispensing
CREATE TABLE pre.dispensing (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  wo_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  lot_id BIGINT NOT NULL,
  target_qty NUMERIC(19,4) NOT NULL,
  actual_qty NUMERIC(19,4) NOT NULL,
  weighed_by BIGINT,
  verified_by BIGINT,
  barcode_scanned VARCHAR(100)
);

-- PRE-07 In-Process Control (IPC)
CREATE TABLE pre.ipc_entry (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  wo_id BIGINT NOT NULL,
  stage_name VARCHAR(100) NOT NULL,
  param_name VARCHAR(100) NOT NULL,
  target_val VARCHAR(50),
  measured_val VARCHAR(50) NOT NULL,
  pass BOOLEAN NOT NULL,
  operator_id BIGINT
);

-- PRE-11 Downtime & Kendala Lini
CREATE TABLE pre.downtime_log (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  line_id BIGINT NOT NULL,
  wo_id BIGINT,
  machine_name VARCHAR(100),
  reason_category VARCHAR(100) NOT NULL,
  start_time TIMESTAMPTZ NOT NULL,
  end_time TIMESTAMPTZ,
  duration_min INT,
  action_taken TEXT
);

-- PRE-13 Line Clearance
CREATE TABLE pre.line_clearance (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  line_id BIGINT NOT NULL,
  wo_id BIGINT NOT NULL,
  checked_by BIGINT,
  qa_inspector_id BIGINT,
  cleanliness_pass BOOLEAN DEFAULT TRUE,
  previous_batch_cleared BOOLEAN DEFAULT TRUE,
  status_result VARCHAR(20) DEFAULT 'PASS'
);

-- PRE-20 Register Mesin & Peralatan
CREATE TABLE pre.machine (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  code VARCHAR(50) UNIQUE NOT NULL,
  name VARCHAR(255) NOT NULL,
  line_id BIGINT,
  brand VARCHAR(100),
  model VARCHAR(100),
  status VARCHAR(30) DEFAULT 'OPERATIONAL', -- OPERATIONAL, MAINTENANCE, UNFIT
  qualification_status VARCHAR(30) DEFAULT 'QUALIFIED',
  last_pm_date DATE,
  next_pm_date DATE
);

-- PRE-22 Work Request (Laporan Kerusakan)
CREATE TABLE pre.work_request (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  machine_id BIGINT,
  requester_id BIGINT NOT NULL,
  priority VARCHAR(20) DEFAULT 'NORMAL',
  description TEXT NOT NULL
);

-- PRE-23 Work Order Maintenance
CREATE TABLE pre.maintenance_order (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  request_id BIGINT,
  machine_id BIGINT NOT NULL,
  maintenance_type VARCHAR(50) DEFAULT 'CORRECTIVE', -- CORRECTIVE, PREVENTIVE
  technician_id BIGINT,
  start_time TIMESTAMPTZ,
  end_time TIMESTAMPTZ,
  cause_analysis TEXT,
  sparepart_used_json TEXT,
  cost_amount NUMERIC(19,2) DEFAULT 0
);

-- PRE-25 Kalibrasi & Kualifikasi
CREATE TABLE pre.calibration (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  machine_id BIGINT NOT NULL,
  cert_no VARCHAR(100),
  calibration_date DATE NOT NULL,
  next_due_date DATE NOT NULL,
  passed BOOLEAN DEFAULT TRUE,
  cert_file_url VARCHAR(255)
);

-- PRE-26 Log Utilitas
CREATE TABLE pre.utility_log (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  log_date DATE NOT NULL,
  shift_name VARCHAR(50),
  electricity_kwh NUMERIC(12,2),
  water_m3 NUMERIC(12,2),
  steam_bar NUMERIC(6,2),
  hvac_temp_c NUMERIC(4,1),
  hvac_humidity_rh NUMERIC(4,1),
  hvac_pressure_diff_pa NUMERIC(6,1)
);


-- =====================================================================
-- 4. HC (Human Capital Phase 3: Rekrutmen, Training, Disiplin)
-- =====================================================================

-- HC-04 Permintaan Tenaga Kerja & Rekrutmen
CREATE TABLE hc.recruitment_request (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  position_id BIGINT NOT NULL,
  headcount_needed INT NOT NULL,
  target_date DATE,
  justification TEXT NOT NULL
);

-- HC-11 Pelatihan & Matriks Training
CREATE TABLE hc.training_record (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  topic VARCHAR(255) NOT NULL,
  trainer VARCHAR(100),
  training_date DATE NOT NULL,
  duration_hours NUMERIC(4,1),
  qualification_id BIGINT,
  attendees_summary TEXT
);

-- HC-15 Disiplin & Peraturan Perusahaan
CREATE TABLE hc.disciplinary_action (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  doc_no VARCHAR(50) UNIQUE NOT NULL,
  doc_date DATE,
  status VARCHAR(20) NOT NULL,
  submitted_at TIMESTAMPTZ,
  approved_by BIGINT,
  approved_at TIMESTAMPTZ,
  posted_by BIGINT,
  posted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  cancelled_by BIGINT,
  cancel_reason VARCHAR(255),
  plant_id BIGINT,
  employee_id BIGINT NOT NULL,
  action_level VARCHAR(30) NOT NULL, -- TEGURAN, SP1, SP2, SP3, PHK
  incident_date DATE NOT NULL,
  violation_clause VARCHAR(100),
  description TEXT NOT NULL,
  valid_until DATE
);
