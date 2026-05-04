import type { NextFunction, Request, Response } from "express";
import { env } from "../config/env";
import { HttpError } from "../core/httpError";

export function adminAuth(req: Request, _res: Response, next: NextFunction) {
  const adminKey = req.header("x-admin-key");

  if (!adminKey || adminKey !== env.ADMIN_API_KEY) {
    return next(new HttpError(401, "Chave administrativa inválida."));
  }

  next();
}
