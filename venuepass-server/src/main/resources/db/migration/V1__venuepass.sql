CREATE TABLE venue (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(160) NOT NULL,
  address VARCHAR(255) NOT NULL,
  capacity INT NOT NULL
);

CREATE TABLE user_account (
  username VARCHAR(100) PRIMARY KEY,
  password_hash VARCHAR(100) NOT NULL
);

CREATE TABLE payment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  reservation_id BIGINT NOT NULL UNIQUE,
  external_reference VARCHAR(100) NOT NULL UNIQUE,
  amount DECIMAL(12,2) NOT NULL,
  paid_at DATETIME NOT NULL,
  refund_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
  refund_reference VARCHAR(100) UNIQUE
);

CREATE TABLE `event` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  venue_id BIGINT NOT NULL,
  title VARCHAR(160) NOT NULL,
  description TEXT,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  FOREIGN KEY (venue_id) REFERENCES venue(id)
);

CREATE TABLE `session` (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id BIGINT NOT NULL,
  starts_at DATETIME NOT NULL,
  ends_at DATETIME NOT NULL,
  price DECIMAL(12,2) NOT NULL,
  total_capacity INT NOT NULL,
  available INT NOT NULL,
  locked INT NOT NULL DEFAULT 0,
  hot BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  FOREIGN KEY (event_id) REFERENCES `event`(id),
  CONSTRAINT ck_session_inventory CHECK (available >= 0 AND locked >= 0 AND available + locked <= total_capacity)
);

CREATE TABLE reservation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  username VARCHAR(100) NOT NULL,
  participant_name VARCHAR(160) NOT NULL,
  status VARCHAR(20) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  code VARCHAR(40) NOT NULL,
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uq_reservation_session_user (session_id, username),
  UNIQUE KEY uq_reservation_code (code),
  INDEX ix_reservation_expiry (status, expires_at),
  FOREIGN KEY (session_id) REFERENCES `session`(id)
);

CREATE TABLE reservation_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  reservation_id BIGINT NOT NULL,
  old_status VARCHAR(20),
  new_status VARCHAR(20) NOT NULL,
  actor VARCHAR(100) NOT NULL,
  occurred_at DATETIME NOT NULL,
  FOREIGN KEY (reservation_id) REFERENCES reservation(id)
);

CREATE TABLE verification (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  reservation_id BIGINT NOT NULL UNIQUE,
  operator_name VARCHAR(100) NOT NULL,
  device VARCHAR(100) NOT NULL,
  verified_at DATETIME NOT NULL,
  FOREIGN KEY (reservation_id) REFERENCES reservation(id)
);

ALTER TABLE payment ADD CONSTRAINT fk_payment_reservation FOREIGN KEY (reservation_id) REFERENCES reservation(id);

CREATE TABLE inventory_release (
  reservation_id BIGINT PRIMARY KEY,
  session_id BIGINT NOT NULL,
  username VARCHAR(100) NOT NULL,
  processed BOOLEAN NOT NULL DEFAULT FALSE,
  FOREIGN KEY (reservation_id) REFERENCES reservation(id)
);
