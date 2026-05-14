import express from "express";
import cors from "cors";
import { env } from "./config/env";
import { getDatabaseHealth } from "./db/prisma";
import { sessionsRouter } from "./modules/sessions/sessions.controller";
import { webhooksRouter } from "./modules/webhooks/webhooks.controller";
import { adminRouter } from "./modules/admin/admin.controller";
import { errorHandler } from "./middleware/errorHandler";
import { stationsRouter } from "./modules/stations/stations.controller";
import { integrationsRouter } from "./modules/integrations/integrations.controller";

export function buildApp() {
  const app = express();

  app.use(cors());
  app.use(express.json({ limit: "2mb" }));

  app.get("/", (_req, res) => {
    res.json({
      ok: true,
      service: "xp-arcade-backend",
      message: "Backend online. Use /health and /api/* endpoints."
    });
  });

  app.get("/health", async (_req, res) => {
    const db = await getDatabaseHealth();
    res.status(db.ok ? 200 : 503).json({
      ok: db.ok,
      service: "xp-arcade-backend",
      env: {
        nodeEnv: env.NODE_ENV,
        paymentProvider: env.PAYMENT_PROVIDER,
        hasDatabaseUrl: Boolean(env.DATABASE_URL),
        hasDirectUrl: Boolean(env.DIRECT_URL),
        hasStationTokenSalt: Boolean(env.STATION_TOKEN_SALT),
        hasAdminApiKey: Boolean(env.ADMIN_API_KEY),
        backendPublicUrl: env.BACKEND_PUBLIC_URL
      },
      db
    });
  });

  app.use("/api/sessions", sessionsRouter);
  app.use("/api/stations", stationsRouter);
  app.use("/api/webhooks", webhooksRouter);
  app.use("/api/admin", adminRouter);
  app.use("/api/integrations", integrationsRouter);

  app.use(errorHandler);

  return app;
}
