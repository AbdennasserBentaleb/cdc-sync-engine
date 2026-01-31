-- Schema initialisation for integration tests.
-- Testcontainers starts a blank PostgreSQL instance; with ddl-auto=none
-- Hibernate will NOT create tables, so we must do it here via Spring's
-- SQL-init mechanism (spring.sql.init.mode=always).

CREATE TABLE IF NOT EXISTS orders (
    id            SERIAL PRIMARY KEY,
    customer_id   VARCHAR(255) NOT NULL,
    status        VARCHAR(50)  NOT NULL,
    total_amount  NUMERIC(10, 2) NOT NULL,
    created_at    TIMESTAMP DEFAULT NOW()
);
