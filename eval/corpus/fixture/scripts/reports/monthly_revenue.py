"""Monthly revenue report, read-only over the billing database."""

import csv
import sys
from collections import defaultdict
from datetime import date

QUERY = """
    SELECT p.paid_at, p.amount_cents
    FROM payments p
    JOIN invoices i ON i.id = p.invoice_id
    WHERE i.status = 'PAID'
"""


def month_key(paid_at: date) -> str:
    return f"{paid_at.year:04d}-{paid_at.month:02d}"


def aggregate_by_month(rows):
    totals = defaultdict(int)
    for paid_at, amount_cents in rows:
        totals[month_key(paid_at)] += amount_cents
    return dict(sorted(totals.items()))


def write_csv(totals, out=sys.stdout):
    writer = csv.writer(out)
    writer.writerow(["month", "revenue_cents"])
    for month, cents in totals.items():
        writer.writerow([month, cents])


def main(connection):
    with connection.cursor() as cursor:
        cursor.execute(QUERY)
        write_csv(aggregate_by_month(cursor.fetchall()))
