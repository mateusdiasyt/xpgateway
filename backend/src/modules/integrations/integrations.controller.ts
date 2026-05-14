import { Prisma, SessionStatus } from "@prisma/client";
import { Router } from "express";
import { z } from "zod";
import { env } from "../../config/env";
import { hashStationToken } from "../../core/stationToken";
import { HttpError } from "../../core/httpError";
import { prisma } from "../../db/prisma";
import { pdvIntegrationAuth } from "../../middleware/pdvIntegrationAuth";
import { releaseSessionFromPdv } from "../sessions/sessions.service";

const pdvReleaseSchema = z.object({
  integrationId: z.string().min(1).optional(),
  saleId: z.string().min(1),
  stationId: z.string().min(1),
  planCode: z.string().min(1).optional(),
  durationMinutes: z.number().int().positive().max(24 * 60),
  amount: z.number().nonnegative().optional(),
  paidAt: z.string().datetime().optional(),
  operator: z.string().optional(),
  customerId: z.string().optional()
});

const tvStatusQuerySchema = z.object({
  stationId: z.string().min(1)
});

function readJsonString(value: Prisma.JsonValue | null | undefined, key: string): string | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const found = (value as Prisma.JsonObject)[key];
  if (typeof found !== "string") return null;
  return found.trim() || null;
}

export const integrationsRouter = Router();

integrationsRouter.post("/pdv/release", pdvIntegrationAuth, async (req, res, next) => {
  try {
    const payload = pdvReleaseSchema.parse(req.body);
    const result = await releaseSessionFromPdv({
      saleId: payload.saleId,
      stationId: payload.stationId,
      durationMinutes: payload.durationMinutes,
      amount: payload.amount,
      paidAt: payload.paidAt ? new Date(payload.paidAt) : undefined,
      operator: payload.operator,
      planCode: payload.planCode,
      customerId: payload.customerId
    });

    return res.status(result.idempotent ? 200 : 201).json(result);
  } catch (error) {
    return next(error);
  }
});

integrationsRouter.get("/tv/status", async (req, res, next) => {
  try {
    const { stationId } = tvStatusQuerySchema.parse(req.query);
    const deviceKey = req.header("x-device-key")?.trim();
    if (!deviceKey) {
      throw new HttpError(401, "Cabecalho x-device-key ausente.");
    }

    const station = await prisma.station.findUnique({ where: { id: stationId } });
    if (!station || !station.isActive) {
      throw new HttpError(404, "Estacao nao encontrada ou inativa.");
    }

    const expectedHash = hashStationToken(deviceKey, env.STATION_TOKEN_SALT);
    if (expectedHash !== station.stationTokenHash) {
      throw new HttpError(403, "Device key invalida.");
    }

    const now = new Date();

    await prisma.session.updateMany({
      where: {
        stationId,
        status: {
          in: [SessionStatus.PAID, SessionStatus.ACTIVE]
        },
        expiresAt: {
          lte: now
        }
      },
      data: {
        status: SessionStatus.EXPIRED
      }
    });

    const activeSession = await prisma.session.findFirst({
      where: {
        stationId,
        status: {
          in: [SessionStatus.PAID, SessionStatus.ACTIVE]
        },
        expiresAt: {
          gt: now
        }
      },
      include: {
        payment: true
      },
      orderBy: {
        expiresAt: "desc"
      }
    });

    if (!activeSession || !activeSession.expiresAt) {
      return res.json({
        stationId,
        status: "INACTIVE",
        saleId: null,
        planCode: null,
        unlockedUntil: null,
        remainingSeconds: 0,
        serverTime: now.toISOString()
      });
    }

    const remainingSeconds = Math.max(
      0,
      Math.floor((activeSession.expiresAt.getTime() - now.getTime()) / 1000)
    );

    const saleId = activeSession.payment?.providerPaymentId ?? null;
    const planCode = readJsonString(activeSession.payment?.rawPayload, "planCode");

    return res.json({
      stationId,
      status: "ACTIVE",
      saleId,
      planCode,
      unlockedUntil: activeSession.expiresAt.toISOString(),
      remainingSeconds,
      serverTime: now.toISOString()
    });
  } catch (error) {
    return next(error);
  }
});

