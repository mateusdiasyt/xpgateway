import express from "express";
import cors from "cors";
import { sessionsRouter } from "./modules/sessions/sessions.controller";
import { webhooksRouter } from "./modules/webhooks/webhooks.controller";
import { adminRouter } from "./modules/admin/admin.controller";
import { errorHandler } from "./middleware/errorHandler";
import { stationsRouter } from "./modules/stations/stations.controller";

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

  app.get("/health", (_req, res) => {
    res.json({ ok: true, service: "xp-arcade-backend" });
  });

  app.use("/api/sessions", sessionsRouter);
  app.use("/api/stations", stationsRouter);
  app.use("/api/webhooks", webhooksRouter);
  app.use("/api/admin", adminRouter);

  app.use(errorHandler);

  return app;
}
