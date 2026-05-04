import { prisma } from "../../db/prisma";
import { moneyToNumber } from "../../core/money";
import { HttpError } from "../../core/httpError";

const FIXED_PAYMENT_DURATION_MINUTES = 20;

export async function getStationWithPricing(stationId: string) {
  const station = await prisma.station.findUnique({
    where: { id: stationId },
    include: {
      pricingOptions: {
        where: { enabled: true },
        orderBy: { durationMinutes: "asc" }
      }
    }
  });

  if (!station) {
    throw new HttpError(404, "Estação não encontrada.");
  }

  const globalPricing = await prisma.pricingOption.findMany({
    where: {
      stationId: null,
      enabled: true
    },
    orderBy: {
      durationMinutes: "asc"
    }
  });

  const mergedByDuration = new Map<number, { label: string; durationMinutes: number; amount: number }>();

  for (const option of globalPricing) {
    mergedByDuration.set(option.durationMinutes, {
      label: option.label,
      durationMinutes: option.durationMinutes,
      amount: moneyToNumber(option.amount)
    });
  }

  for (const option of station.pricingOptions) {
    mergedByDuration.set(option.durationMinutes, {
      label: option.label,
      durationMinutes: option.durationMinutes,
      amount: moneyToNumber(option.amount)
    });
  }

  return {
    id: station.id,
    name: station.name,
    isActive: station.isActive,
    pricingOptions: Array.from(mergedByDuration.values())
      .filter((option) => option.durationMinutes === FIXED_PAYMENT_DURATION_MINUTES)
      .sort((a, b) => a.durationMinutes - b.durationMinutes)
  };
}

export async function getLastPaymentByStation(stationId: string) {
  const payment = await prisma.payment.findFirst({
    where: {
      session: {
        stationId
      }
    },
    orderBy: {
      createdAt: "desc"
    },
    include: {
      session: true
    }
  });

  if (!payment) {
    return null;
  }

  return {
    paymentId: payment.providerPaymentId,
    status: payment.status,
    sessionId: payment.sessionId,
    amount: moneyToNumber(payment.session.amount),
    createdAt: payment.createdAt,
    stationId
  };
}
