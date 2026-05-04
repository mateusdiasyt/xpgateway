import axios from "axios";
import type { CreatePaymentInput, CreatePaymentResult, PaymentProvider, ProviderPaymentStatus, SyncPaymentInput } from "./types";
import { env } from "../../config/env";

export class PaymentProviderSicoob implements PaymentProvider {
  readonly name = "SICOOB" as const;

  async createPayment(input: CreatePaymentInput): Promise<CreatePaymentResult> {
    if (!env.SICOOB_CLIENT_ID || !env.SICOOB_CLIENT_SECRET) {
      throw new Error("Sicoob não configurado: defina client_id/client_secret no backend.");
    }

    // Estrutura preparada para integração real:
    // 1) OAuth client_credentials com certificado mTLS
    // 2) Criação da cobrança Pix imediata
    // 3) Retorno de payload EMV (copia e cola) e QR
    // Neste MVP retornamos erro orientando uso do mock.

    await axios.get(`${env.SICOOB_BASE_URL}/health`).catch(() => null);

    throw new Error(
      `Provider Sicoob preparado, mas não implementado neste MVP. Use PAYMENT_PROVIDER=MOCK para fluxo funcional imediato.`
    );
  }

  async syncPaymentStatus(_input: SyncPaymentInput): Promise<ProviderPaymentStatus | null> {
    return null;
  }

  validateWebhook(headers: Record<string, string | string[] | undefined>, payload: unknown): boolean {
    const signature = headers["x-sicoob-signature"];
    if (!env.SICOOB_WEBHOOK_SECRET) {
      return false;
    }

    // Placeholder de validação. Na integração real, aplicar validação oficial do Sicoob.
    return Boolean(signature && payload);
  }
}
