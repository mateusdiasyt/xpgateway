import { env } from "../../config/env";
import { PaymentProviderMock } from "./paymentProvider.mock";
import { PaymentProviderSicoob } from "./paymentProvider.sicoob";
import type { PaymentProvider } from "./types";

let cachedProvider: PaymentProvider | null = null;

export function getPaymentProvider(): PaymentProvider {
  if (cachedProvider) {
    return cachedProvider;
  }

  cachedProvider = env.PAYMENT_PROVIDER === "SICOOB" ? new PaymentProviderSicoob() : new PaymentProviderMock();
  return cachedProvider;
}
