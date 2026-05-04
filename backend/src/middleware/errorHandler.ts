import type { NextFunction, Request, Response } from "express";
import { ZodError } from "zod";
import { HttpError } from "../core/httpError";
import { logger } from "../core/logger";

export function errorHandler(err: unknown, _req: Request, res: Response, _next: NextFunction) {
  if (err instanceof ZodError) {
    return res.status(400).json({
      message: "Payload inválido.",
      issues: err.issues
    });
  }

  if (err instanceof HttpError) {
    return res.status(err.statusCode).json({
      message: err.message,
      details: err.details
    });
  }

  logger.error("Erro não tratado", err);
  return res.status(500).json({
    message: "Erro interno do servidor."
  });
}
