export type ProviderPaymentStatus = "PENDING" | "PAID" | "EXPIRED" | "CANCELLED" | "FAILED";

export interface CreatePaymentInput {
  sessionId: string;
  stationId: string;
  durationMinutes: number;
  amount: number;
}

export interface CreatePaymentResult {
  providerPaymentId: string;
  qrCode: string;
  pixCopiaECola: string;
  status: ProviderPaymentStatus;
  rawPayload?: Record<string, unknown>;
}

export interface SyncPaymentInput {
  providerPaymentId: string;
}

export interface PaymentProvider {
  readonly name: "MOCK" | "SICOOB";
  createPayment(input: CreatePaymentInput): Promise<CreatePaymentResult>;
  syncPaymentStatus?(input: SyncPaymentInput): Promise<ProviderPaymentStatus | null>;
  validateWebhook?(headers: Record<string, string | string[] | undefined>, payload: unknown): boolean;
}
