import { formatCurrency } from "../utils/formatCurrency";

const BASE_URL = "/api/v1";
const MAX_ATTEMPTS = 3;
const INITIAL_BACKOFF_MS = 200;

export interface InvoiceSummary {
  id: string;
  totalCents: number;
  status: "OPEN" | "PAID" | "OVERDUE";
}

async function withRetry<T>(operation: () => Promise<T>): Promise<T> {
  let lastError: unknown;
  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    try {
      return await operation();
    } catch (error) {
      lastError = error;
      if (attempt < MAX_ATTEMPTS) {
        const delay = INITIAL_BACKOFF_MS * 2 ** (attempt - 1);
        await new Promise((resolve) => setTimeout(resolve, delay));
      }
    }
  }
  throw lastError;
}

export async function fetchInvoices(customerId: string): Promise<InvoiceSummary[]> {
  return withRetry(async () => {
    const response = await fetch(`${BASE_URL}/customers/${customerId}/invoices`);
    if (!response.ok) {
      throw new Error(`request failed with status ${response.status}`);
    }
    return (await response.json()) as InvoiceSummary[];
  });
}

export function describeInvoice(invoice: InvoiceSummary): string {
  return `${invoice.id}: ${formatCurrency(invoice.totalCents)} (${invoice.status})`;
}
