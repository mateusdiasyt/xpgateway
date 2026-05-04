import { Router } from "express";
import { z } from "zod";
import { stationAuth } from "../../middleware/stationAuth";
import { HttpError } from "../../core/httpError";
import { createSessionPayment, getSessionStatus } from "./sessions.service";

const createPaymentSchema = z.object({
  stationId: z.string().min(1),
  durationMinutes: z.number().int().positive(),
  amount: z.number().positive().optional()
});

export const sessionsRouter = Router();

sessionsRouter.post("/create-payment", stationAuth, async (req, res, next) => {
  try {
    const payload = createPaymentSchema.parse(req.body);

    if (!req.station) {
      throw new HttpError(401, "Estação não autenticada.");
    }

    if (payload.stationId !== req.station.id) {
      throw new HttpError(403, "stationId do body difere da estação autenticada.");
    }

    const created = await createSessionPayment({
      stationId: payload.stationId,
      durationMinutes: payload.durationMinutes,
      clientAmount: payload.amount
    });

    return res.status(201).json(created);
  } catch (error) {
    return next(error);
  }
});

sessionsRouter.get("/:sessionId/status", stationAuth, async (req, res, next) => {
  try {
    const sessionId = req.params.sessionId;

    if (!req.station) {
      throw new HttpError(401, "Estação não autenticada.");
    }

    const status = await getSessionStatus(sessionId, req.station.id);
    return res.json(status);
  } catch (error) {
    return next(error);
  }
});
