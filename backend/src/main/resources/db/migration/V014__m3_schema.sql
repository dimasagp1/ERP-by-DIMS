CREATE SCHEMA qms;

CREATE TABLE qms.batch_release (
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
  lot_id BIGINT,
  wo_id BIGINT,
  decision VARCHAR(255),
  reviewed_by VARCHAR(255),
  released_at TIMESTAMPTZ
);

CREATE TABLE qms.capa (
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
  deviation_id BIGINT,
  complaint_id BIGINT,
  audit_id BIGINT,
  pic_id BIGINT,
  due_date DATE,
  effectiveness VARCHAR(255)
);

CREATE TABLE qms.change_control (
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
  type VARCHAR(255),
  description VARCHAR(255),
  impact VARCHAR(255),
  risk_score INT
);

CREATE TABLE qms.coa (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  lot_id BIGINT,
  issued_at TIMESTAMPTZ,
  signed_by VARCHAR(255)
);

CREATE TABLE qms.complaint (
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
  partner_id BIGINT,
  lot_id BIGINT,
  description VARCHAR(255),
  decision VARCHAR(255)
);

CREATE TABLE qms.deviation (
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
  source_type VARCHAR(255),
  source_id BIGINT,
  deviation_class VARCHAR(255),
  root_cause VARCHAR(255),
  lot_ids VARCHAR(255)
);

CREATE TABLE qms.document (
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
  title VARCHAR(255)
);

CREATE TABLE qms.sample (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  lot_id BIGINT,
  source VARCHAR(255),
  qty DECIMAL(19,4),
  sampled_by VARCHAR(255)
);

CREATE TABLE qms.stability_study (
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
  item_id BIGINT,
  lot_id BIGINT,
  study_condition VARCHAR(255),
  timepoints VARCHAR(255)
);

CREATE TABLE qms.test_result (
  id BIGSERIAL PRIMARY KEY,
  version INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  created_by BIGINT,
  updated_at TIMESTAMPTZ,
  updated_by BIGINT,
  sample_id BIGINT,
  spec_param_id BIGINT,
  value VARCHAR(255),
  pass BOOLEAN
);


CREATE TABLE qms.doc_version (
  id BIGSERIAL PRIMARY KEY,
  document_id BIGINT,
  version INT,
  effective_date DATE,
  status VARCHAR(255),
  file_url VARCHAR(255)
);
