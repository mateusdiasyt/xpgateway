import { Router } from "express";
import { z } from "zod";
import { pdvIntegrationAuth } from "../../middleware/pdvIntegrationAuth";
import { releaseSessionFromPdv } from "../sessions/sessions.service";

const pdvReleaseSchema = z.object({
  integrationId: z.string().min(1),
  saleId: z.string().min(1),
  stationId: z.string().min(1),
  planCode: z.string().min(1).optional(),
  durationMinutes: z.number().int().positive().max(24 * 60),
  amount: z.number().nonnegative().optional(),
  paidAt: z.string().datetime().optional(),
  operator: z.string().optional(),
  customerId: z.string().optional()
});

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

