import type { NextFunction, Request, Response } from "express";
import { env } from "../config/env";
import { HttpError } from "../core/httpError";
import { hashStationToken } from "../core/stationToken";
import { prisma } from "../db/prisma";

export async function stationAuth(req: Request, _res: Response, next: NextFunction) {
  try {
    const stationId = req.header("x-station-id");
    const stationToken = req.header("x-station-token");

    if (!stationId || !stationToken) {
      return next(new HttpError(401, "Cabecalhos de estacao ausentes."));
    }

    const station = await prisma.station.findUnique({ where: { id: stationId } });

    if (!station || !station.isActive) {
      return next(new HttpError(403, "Estacao invalida ou inativa."));
    }

    const expectedHash = hashStationToken(stationToken, env.STATION_TOKEN_SALT);
    if (expectedHash !== station.stationTokenHash) {
      return next(new HttpError(403, "Token da estacao invalido."));
    }

    req.station = station;
    next();
  } catch (error) {
    if ((error as { code?: string })?.code === "P2021") {
      return next(new HttpError(503, "Banco nao inicializado. Execute schema e seed no PostgreSQL."));
    }
    return next(error);
  }
}
