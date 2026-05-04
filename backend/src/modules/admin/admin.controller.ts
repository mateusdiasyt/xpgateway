import { Router } from "express";
import { z } from "zod";
import { adminAuth } from "../../middleware/adminAuth";
import { endSession, forceUnlockStation, markSessionPaidByProviderPaymentId } from "../sessions/sessions.service";
import { prisma } from "../../db/prisma";

const forceUnlockSchema = z.object({
  durationMinutes: z.number().int().positive()
});

export const adminRouter = Router();

adminRouter.use(adminAuth);

adminRouter.post("/stations/:stationId/force-unlock", async (req, res, next) => {
  try {
    const stationId = req.params.stationId;
    const payload = forceUnlockSchema.parse(req.body);

    const session = await forceUnlockStation(stationId, payload.durationMinutes, "admin_api");
    return res.status(201).json({
      sessionId: session.id,
      status: session.status,
      expiresAt: session.expiresAt
    });
  } catch (error) {
    return next(error);
  }
});

adminRouter.post("/sessions/:sessionId/end", async (req, res, next) => {
  try {
    const session = await endSession(req.params.sessionId, "admin_api");
    return res.json({
      sessionId: session.id,
      status: session.status,
      expiresAt: session.expiresAt
    });
  } catch (error) {
    return next(error);
  }
});

adminRouter.post("/payments/:providerPaymentId/mock-confirm", async (req, res, next) => {
  try {
    const confirmed = await markSessionPaidByProviderPaymentId(req.params.providerPaymentId, new Date());
    return res.json({
      status: "PAID",
      ...confirmed
    });
  } catch (error) {
    return next(error);
  }
});

adminRouter.get("/stations", async (_req, res, next) => {
  try {
    const stations = await prisma.station.findMany({
      select: {
        id: true,
        name: true,
        isActive: true,
        createdAt: true
      },
      orderBy: {
        createdAt: "asc"
      }
    });

    return res.json({ stations });
  } catch (error) {
    return next(error);
  }
});
