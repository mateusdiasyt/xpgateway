import QRCode from "qrcode";
import type { CreatePaymentInput, CreatePaymentResult, PaymentProvider, ProviderPaymentStatus, SyncPaymentInput } from "./types";

function buildMockPixPayload(input: CreatePaymentInput): string {
  const value = input.amount.toFixed(2);
  return `00020126580014BR.GOV.BCB.PIX0136xp-arcade-${input.stationId}-${input.sessionId}520400005303986540${value.length}${value}5802BR5925XP ARCADE E BAR6009SAO PAULO62070503***6304ABCD`;
}

export class PaymentProviderMock implements PaymentProvider {
  readonly name = "MOCK" as const;

  async createPayment(input: CreatePaymentInput): Promise<CreatePaymentResult> {
    const pixCopiaECola = buildMockPixPayload(input);
    const qrCode = await QRCode.toDataURL(pixCopiaECola, {
      errorCorrectionLevel: "M",
      margin: 1,
      width: 800
    });

    return {
      providerPaymentId: `mock_${input.sessionId}`,
      qrCode,
      pixCopiaECola,
      status: "PENDING",
      rawPayload: {
        provider: "MOCK",
        createdAt: new Date().toISOString()
      }
    };
  }

  async syncPaymentStatus(_input: SyncPaymentInput): Promise<ProviderPaymentStatus | null> {
    return null;
  }

  validateWebhook(): boolean {
    return true;
  }
}
