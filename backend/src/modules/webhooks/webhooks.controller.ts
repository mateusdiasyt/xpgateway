import { Router } from "express";
import { z } from "zod";
import { HttpError } from "../../core/httpError";
import { prisma } from "../../db/prisma";
import { getPaymentProvider } from "../payments/paymentProvider";
import { markSessionPaidByProviderPaymentId } from "../sessions/sessions.service";

const webhookSchema = z.object({
  providerPaymentId: z.string().min(1),
  status: z.enum(["PENDING", "PAID", "EXPIRED", "CANCELLED", "FAILED"]),
  paidAt: z.string().datetime().optional(),
  raw: z.unknown().optional()
});

export const webhooksRouter = Router();

webhooksRouter.post("/sicoob", async (req, res, next) => {
  try {
    const provider = getPaymentProvider();

    if (provider.name !== "SICOOB") {
      throw new HttpError(400, "Webhook Sicoob recebido, mas provider ativo não é SICOOB.");
    }

    const valid = provider.validateWebhook?.(req.headers, req.body) ?? false;
    if (!valid) {
      throw new HttpError(401, "Assinatura de webhook inválida.");
    }

    const payload = webhookSchema.parse(req.body);

    if (payload.status === "PAID") {
      const paidAt = payload.paidAt ? new Date(payload.paidAt) : new Date();
      const result = await markSessionPaidByProviderPaymentId(payload.providerPaymentId, paidAt);
      return res.json({
        ok: true,
        status: "PAID",
        ...result
      });
    }

    await prisma.payment.updateMany({
      where: { providerPaymentId: payload.providerPaymentId },
      data: {
        status: payload.status,
        rawPayload: payload.raw as object
      }
    });

    return res.json({ ok: true, status: payload.status });
  } catch (error) {
    return next(error);
  }
});

// Endpoint utilitário para MVP/mock sem webhook real.
webhooksRouter.post("/mock", async (req, res, next) => {
  try {
    const payload = webhookSchema.parse(req.body);
    if (payload.status !== "PAID") {
      return res.status(400).json({ message: "No mock endpoint, only PAID is supported." });
    }

    const paidAt = payload.paidAt ? new Date(payload.paidAt) : new Date();
    const result = await markSessionPaidByProviderPaymentId(payload.providerPaymentId, paidAt);

    return res.json({
      ok: true,
      status: "PAID",
      ...result
    });
  } catch (error) {
    return next(error);
  }
});
