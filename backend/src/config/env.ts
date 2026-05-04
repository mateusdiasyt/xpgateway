import dotenv from "dotenv";
import { z } from "zod";

dotenv.config();

const envSchema = z.object({
  NODE_ENV: z.string().catch("development"),
  PORT: z.coerce.number().catch(8080),
  DATABASE_URL: z.string().optional().catch(undefined),
  DIRECT_URL: z.string().optional(),
  PAYMENT_PROVIDER: z.enum(["MOCK", "SICOOB"]).catch("MOCK"),
  STATION_TOKEN_SALT: z.string().catch("local-dev-salt"),
  ADMIN_API_KEY: z.string().catch("change-me-admin-key"),
  BACKEND_PUBLIC_URL: z.string().catch("http://localhost:8080"),

  SICOOB_CLIENT_ID: z.string().optional(),
  SICOOB_CLIENT_SECRET: z.string().optional(),
  SICOOB_PIX_KEY: z.string().optional(),
  SICOOB_TOKEN_URL: z.string().optional(),
  SICOOB_SCOPES: z.string().optional().catch("cob.write cob.read pix.read"),
  SICOOB_CERT_BASE64: z.string().optional(),
  SICOOB_KEY_BASE64: z.string().optional(),
  SICOOB_KEY_PASSPHRASE: z.string().optional(),
  SICOOB_WEBHOOK_SECRET: z.string().optional(),
  SICOOB_BASE_URL: z.string().catch("https://api.sicoob.com.br/pix/api/v2")
});

function normalizeHttpUrl(raw: string): string {
  const value = raw.trim();
  if (!value) return "http://localhost:8080";
  if (/^https?:\/\//i.test(value)) return value;
  return `https://${value}`;
}

const parsed = envSchema.parse(process.env);

if (!parsed.DATABASE_URL) {
  console.warn("[WARN] DATABASE_URL not configured. Database routes may fail.");
}

export const env = {
  ...parsed,
  BACKEND_PUBLIC_URL: normalizeHttpUrl(parsed.BACKEND_PUBLIC_URL),
  SICOOB_BASE_URL: normalizeHttpUrl(parsed.SICOOB_BASE_URL),
  SICOOB_TOKEN_URL: parsed.SICOOB_TOKEN_URL ? normalizeHttpUrl(parsed.SICOOB_TOKEN_URL) : undefined
};
