# Billing service

The billing service issues invoices, receives payment notifications from the
payment gateway and publishes a monthly revenue report.

## Components

- `src/main/java/com/example/billing`: invoice totals, discounts and late fees.
- `src/main/java/com/example/payments`: gateway webhooks. Each notification
  carries an idempotency key, and repeated keys are ignored for 24 hours.
- `web/`: customer portal (TypeScript). `admin/`: back-office tools.
- `scripts/reports`: read-only reports over the database.
- `db/schema.sql`: PostgreSQL schema.

## Money

All amounts are stored and computed as integer cents. Only the user
interfaces format them as Brazilian reais.

## Late fees

An invoice paid after its due date pays a fee per day of delay, after a grace
period, up to a fixed cap. The fee is computed when the invoice total is read.
