import { Router } from "express";
import { z } from "zod";
import { HttpError } from "../../core/httpError";
import { prisma } from "../../db/prisma";
import { getPaymentProvider } from "../payments/paymentProvider";
import { extractPaidPixFromSicoobWebhook } from "../payments/paymentProvider.sicoob";
import { markSessionPaidByProviderPaymentId } from "../sessions/sessions.service";

const legacyWebhookSchema = z.object({
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
      throw new HttpError(400, "Webhook Sicoob recebido, mas provider ativo nao e SICOOB.");
    }

    const valid = provider.validateWebhook?.(req.headers, req.body) ?? false;
    if (!valid) {
      throw new HttpError(401, "Webhook Sicoob invalido (assinatura/formato).");
    }

    const pixItems = extractPaidPixFromSicoobWebhook(req.body);
    if (pixItems.length > 0) {
      let paidCount = 0;
      let ignoredCount = 0;
      const sessionIds: string[] = [];

      for (const pix of pixItems) {
        try {
          const result = await markSessionPaidByProviderPaymentId(pix.txid, pix.paidAt);
          await prisma.payment.updateMany({
            where: { providerPaymentId: pix.txid },
            data: {
              status: "PAID",
              rawPayload: req.body as object
            }
          });
          sessionIds.push(result.sessionId);
          paidCount += 1;
        } catch (error) {
          if (error instanceof HttpError && error.statusCode === 404) {
            ignoredCount += 1;
            continue;
          }
          throw error;
        }
      }

      return res.json({
        ok: true,
        status: "PAID",
        processed: pixItems.length,
        paidCount,
        ignoredCount,
        sessionIds
      });
    }

    const legacy = legacyWebhookSchema.safeParse(req.body);
    if (!legacy.success) {
      throw new HttpError(400, "Payload de webhook nao reconhecido.");
    }

    if (legacy.data.status === "PAID") {
      const paidAt = legacy.data.paidAt ? new Date(legacy.data.paidAt) : new Date();
      const result = await markSessionPaidByProviderPaymentId(legacy.data.providerPaymentId, paidAt);
      await prisma.payment.updateMany({
        where: { providerPaymentId: legacy.data.providerPaymentId },
        data: {
          status: "PAID",
          rawPayload: (legacy.data.raw as object) ?? (req.body as object)
        }
      });
      return res.json({
        ok: true,
        status: "PAID",
        ...result
      });
    }

    await prisma.payment.updateMany({
      where: { providerPaymentId: legacy.data.providerPaymentId },
      data: {
        status: legacy.data.status,
        rawPayload: (legacy.data.raw as object) ?? (req.body as object)
      }
    });

    return res.json({ ok: true, status: legacy.data.status });
  } catch (error) {
    return next(error);
  }
});

// Endpoint utilitario para MVP/mock sem webhook real.
webhooksRouter.post("/mock", async (req, res, next) => {
  try {
    const payload = legacyWebhookSchema.parse(req.body);
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

