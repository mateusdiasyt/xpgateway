import axios, { AxiosInstance } from "axios";
import crypto from "crypto";
import https from "https";
import QRCode from "qrcode";
import { env } from "../../config/env";
import type { CreatePaymentInput, CreatePaymentResult, PaymentProvider, ProviderPaymentStatus, SyncPaymentInput } from "./types";

type SicoobCobStatus = "ATIVA" | "CONCLUIDA" | "REMOVIDA_PELO_USUARIO_RECEBEDOR" | "REMOVIDA_PELO_PSP";

interface SicoobTokenResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  scope?: string;
}

interface SicoobCobResponse {
  txid: string;
  status: SicoobCobStatus;
  brcode?: string;
  calendario?: {
    criacao?: string;
    expiracao?: number;
  };
  pix?: Array<{
    endToEndId?: string;
    txid?: string;
    valor?: string;
    horario?: string;
  }>;
  [key: string]: unknown;
}

type ParsedWebhookPix = {
  txid: string;
  paidAt: Date;
  raw: Record<string, unknown>;
};

function mustGetSicoobConfig() {
  if (!env.SICOOB_CLIENT_ID || !env.SICOOB_CLIENT_SECRET) {
    throw new Error("Sicoob nao configurado: defina SICOOB_CLIENT_ID e SICOOB_CLIENT_SECRET.");
  }

  if (!env.SICOOB_PIX_KEY) {
    throw new Error("Sicoob nao configurado: defina SICOOB_PIX_KEY (chave Pix recebedor).");
  }

  if (!env.SICOOB_TOKEN_URL) {
    throw new Error("Sicoob nao configurado: defina SICOOB_TOKEN_URL.");
  }

  if (!env.SICOOB_CERT_BASE64 || !env.SICOOB_KEY_BASE64) {
    throw new Error("Sicoob nao configurado: defina SICOOB_CERT_BASE64 e SICOOB_KEY_BASE64.");
  }

  return {
    clientId: env.SICOOB_CLIENT_ID,
    clientSecret: env.SICOOB_CLIENT_SECRET,
    pixKey: env.SICOOB_PIX_KEY,
    tokenUrl: env.SICOOB_TOKEN_URL,
    baseUrl: env.SICOOB_BASE_URL,
    scopes: env.SICOOB_SCOPES?.trim() || "cob.write cob.read pix.read",
    certPem: Buffer.from(env.SICOOB_CERT_BASE64, "base64").toString("utf-8"),
    keyPem: Buffer.from(env.SICOOB_KEY_BASE64, "base64").toString("utf-8"),
    keyPassphrase: env.SICOOB_KEY_PASSPHRASE
  };
}

function buildTxid(sessionId: string): string {
  const digest = crypto.createHash("sha256").update(`xp-arcade-${sessionId}`).digest("hex");
  return `XP${digest.slice(0, 33)}`;
}

function mapCobStatus(status: SicoobCobStatus): ProviderPaymentStatus {
  if (status === "CONCLUIDA") return "PAID";
  if (status === "ATIVA") return "PENDING";
  return "CANCELLED";
}

function createMutualTlsAgent(certPem: string, keyPem: string, passphrase?: string) {
  return new https.Agent({
    cert: certPem,
    key: keyPem,
    passphrase,
    keepAlive: true,
    minVersion: "TLSv1.2"
  });
}

function normalizeDataUrlImage(mime: string | undefined, bytes: Buffer): string {
  const type = mime?.trim() || "image/png";
  return `data:${type};base64,${bytes.toString("base64")}`;
}

function pickString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value : null;
}

function safeHeaderValue(value: string | string[] | undefined): string {
  if (!value) return "";
  return Array.isArray(value) ? value[0] ?? "" : value;
}

function verifyHmacSignature(payload: unknown, signatureHeader: string, secret: string): boolean {
  if (!signatureHeader) return false;
  const signatureRaw = signatureHeader.replace(/^sha256=/i, "").trim();
  if (!signatureRaw) return false;

  let expected: Buffer;
  let received: Buffer;

  try {
    expected = Buffer.from(signatureRaw, "hex");
    const digest = crypto
      .createHmac("sha256", secret)
      .update(JSON.stringify(payload))
      .digest("hex");
    received = Buffer.from(digest, "hex");
  } catch {
    return false;
  }

  if (expected.length !== received.length) return false;
  return crypto.timingSafeEqual(expected, received);
}

export function extractPaidPixFromSicoobWebhook(payload: unknown): ParsedWebhookPix[] {
  if (!payload || typeof payload !== "object") return [];
  const root = payload as Record<string, unknown>;
  const pixRaw = root.pix;
  if (!Array.isArray(pixRaw)) return [];

  const result: ParsedWebhookPix[] = [];

  for (const item of pixRaw) {
    if (!item || typeof item !== "object") continue;
    const pix = item as Record<string, unknown>;
    const txid = pickString(pix.txid);
    if (!txid) continue;

    const horarioRaw = pickString(pix.horario);
    const paidAt = horarioRaw ? new Date(horarioRaw) : new Date();
    const normalizedPaidAt = Number.isNaN(paidAt.getTime()) ? new Date() : paidAt;

    result.push({
      txid,
      paidAt: normalizedPaidAt,
      raw: pix
    });
  }

  return result;
}

export class PaymentProviderSicoob implements PaymentProvider {
  readonly name = "SICOOB" as const;

  private tokenClient: AxiosInstance | null = null;
  private apiClient: AxiosInstance | null = null;
  private cachedToken: { accessToken: string; expiresAt: number } | null = null;

  private getClients() {
    if (this.tokenClient && this.apiClient) {
      return { tokenClient: this.tokenClient, apiClient: this.apiClient };
    }

    const cfg = mustGetSicoobConfig();
    const httpsAgent = createMutualTlsAgent(cfg.certPem, cfg.keyPem, cfg.keyPassphrase);

    this.tokenClient = axios.create({
      httpsAgent,
      timeout: 15_000
    });

    this.apiClient = axios.create({
      baseURL: cfg.baseUrl,
      httpsAgent,
      timeout: 15_000
    });

    return { tokenClient: this.tokenClient, apiClient: this.apiClient };
  }

  private async getAccessToken(): Promise<string> {
    const now = Date.now();
    if (this.cachedToken && this.cachedToken.expiresAt > now + 15_000) {
      return this.cachedToken.accessToken;
    }

    const cfg = mustGetSicoobConfig();
    const { tokenClient } = this.getClients();

    const form = new URLSearchParams();
    form.set("grant_type", "client_credentials");
    form.set("client_id", cfg.clientId);
    form.set("client_secret", cfg.clientSecret);
    form.set("scope", cfg.scopes);

    const response = await tokenClient.post<SicoobTokenResponse>(cfg.tokenUrl, form.toString(), {
      headers: { "Content-Type": "application/x-www-form-urlencoded" }
    });

    if (!response.data?.access_token) {
      throw new Error("Sicoob OAuth falhou: access_token ausente.");
    }

    const expiresIn = Math.max(30, response.data.expires_in || 300);
    this.cachedToken = {
      accessToken: response.data.access_token,
      expiresAt: now + expiresIn * 1000
    };

    return response.data.access_token;
  }

  private async getAuthHeaders() {
    const cfg = mustGetSicoobConfig();
    const accessToken = await this.getAccessToken();
    return {
      Authorization: `Bearer ${accessToken}`,
      client_id: cfg.clientId
    };
  }

  async createPayment(input: CreatePaymentInput): Promise<CreatePaymentResult> {
    const cfg = mustGetSicoobConfig();
    const { apiClient } = this.getClients();
    const txid = buildTxid(input.sessionId);

    const cobPayload = {
      calendario: {
        expiracao: Math.max(60, input.durationMinutes * 60)
      },
      valor: {
        original: input.amount.toFixed(2)
      },
      chave: cfg.pixKey,
      solicitacaoPagador: `XP Arcade ${input.durationMinutes} min`,
      infoAdicionais: [
        { nome: "estacao", valor: input.stationId },
        { nome: "sessionId", valor: input.sessionId }
      ]
    };

    const cobResponse = await apiClient.put<SicoobCobResponse>(`/cob/${txid}`, cobPayload, {
      headers: await this.getAuthHeaders()
    });

    const brcode = pickString(cobResponse.data?.brcode);
    if (!brcode) {
      throw new Error("Sicoob criou cobranca sem brcode no retorno.");
    }

    let qrCode: string;
    try {
      qrCode = await QRCode.toDataURL(brcode, {
        errorCorrectionLevel: "M",
        margin: 1,
        width: 800
      });
    } catch {
      const imageResponse = await apiClient.get<ArrayBuffer>(`/cob/${txid}/imagem`, {
        headers: await this.getAuthHeaders(),
        responseType: "arraybuffer"
      });
      const mime = typeof imageResponse.headers["content-type"] === "string" ? imageResponse.headers["content-type"] : undefined;
      qrCode = normalizeDataUrlImage(mime, Buffer.from(imageResponse.data));
    }

    return {
      providerPaymentId: txid,
      qrCode,
      pixCopiaECola: brcode,
      status: mapCobStatus(cobResponse.data.status),
      rawPayload: cobResponse.data as unknown as Record<string, unknown>
    };
  }

  async syncPaymentStatus(input: SyncPaymentInput): Promise<ProviderPaymentStatus | null> {
    const { apiClient } = this.getClients();
    const response = await apiClient.get<SicoobCobResponse>(`/cob/${encodeURIComponent(input.providerPaymentId)}`, {
      headers: await this.getAuthHeaders()
    });

    if (!response.data?.status) {
      return null;
    }

    return mapCobStatus(response.data.status);
  }

  validateWebhook(headers: Record<string, string | string[] | undefined>, payload: unknown): boolean {
    const signature = safeHeaderValue(headers["x-sicoob-signature"] ?? headers["x-signature"]);

    if (env.SICOOB_WEBHOOK_SECRET) {
      return verifyHmacSignature(payload, signature, env.SICOOB_WEBHOOK_SECRET);
    }

    // Sem segredo configurado, aceitamos payload no formato esperado e recomendamos validar mTLS no edge.
    return extractPaidPixFromSicoobWebhook(payload).length > 0;
  }
}

