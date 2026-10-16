CREATE TABLE customers (
    id          uuid        PRIMARY KEY,
    email       text        NOT NULL UNIQUE,
    created_at  timestamptz NOT NULL
);

CREATE TABLE invoices (
    id                uuid        PRIMARY KEY,
    customer_id       uuid        NOT NULL REFERENCES customers (id),
    status            text        NOT NULL,
    discount_percent  integer     NOT NULL DEFAULT 0,
    due_date          date        NOT NULL,
    late_fee_cents    bigint      NOT NULL DEFAULT 0,
    CONSTRAINT invoices_status_valid CHECK (status IN ('OPEN', 'PAID', 'OVERDUE')),
    CONSTRAINT invoices_discount_range CHECK (discount_percent BETWEEN 0 AND 100)
);

CREATE TABLE payments (
    id               uuid        PRIMARY KEY,
    invoice_id       uuid        NOT NULL REFERENCES invoices (id),
    idempotency_key  text        NOT NULL UNIQUE,
    amount_cents     bigint      NOT NULL,
    paid_at          date        NOT NULL
);

CREATE INDEX payments_invoice_idx ON payments (invoice_id);
