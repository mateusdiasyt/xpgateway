import type { NextFunction, Request, Response } from "express";
import { env } from "../config/env";
import { HttpError } from "../core/httpError";

export function pdvIntegrationAuth(req: Request, _res: Response, next: NextFunction) {
  const configuredKey = env.PDV_INTEGRATION_KEY?.trim();
  if (!configuredKey) {
    return next(new HttpError(503, "PDV integration key nao configurada no backend."));
  }

  const providedKey = req.header("x-integration-key")?.trim();
  if (!providedKey || providedKey !== configuredKey) {
    return next(new HttpError(401, "Integracao PDV nao autorizada."));
  }

  return next();
}

