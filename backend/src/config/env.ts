import dotenv from "dotenv";
import { z } from "zod";

dotenv.config();

const envSchema = z.object({
  NODE_ENV: z.string().default("development"),
  PORT: z.coerce.number().default(8080),
  DATABASE_URL: z.string().min(1),
  DIRECT_URL: z.string().optional(),
  PAYMENT_PROVIDER: z.enum(["MOCK", "SICOOB"]).default("MOCK"),
  STATION_TOKEN_SALT: z.string().default("local-dev-salt"),
  ADMIN_API_KEY: z.string().default("change-me-admin-key"),
  BACKEND_PUBLIC_URL: z.string().url().default("http://localhost:8080"),

  SICOOB_CLIENT_ID: z.string().optional(),
  SICOOB_CLIENT_SECRET: z.string().optional(),
  SICOOB_CERT_BASE64: z.string().optional(),
  SICOOB_KEY_BASE64: z.string().optional(),
  SICOOB_WEBHOOK_SECRET: z.string().optional(),
  SICOOB_BASE_URL: z.string().default("https://api.sisbr.com.br")
});

export const env = envSchema.parse(process.env);
