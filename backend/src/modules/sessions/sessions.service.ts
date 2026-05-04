import { Prisma, SessionStatus } from "@prisma/client";
import { HttpError } from "../../core/httpError";
import { moneyToNumber } from "../../core/money";
import { prisma } from "../../db/prisma";
import { getPaymentProvider } from "../payments/paymentProvider";
import type { ProviderPaymentStatus } from "../payments/types";

const FIXED_PAYMENT_DURATION_MINUTES = 20;
const FIXED_PAYMENT_AMOUNT = 15.0;
const PENDING_PAYMENT_TTL_MINUTES = 8;

export interface CreateSessionPaymentInput {
  stationId: string;
  durationMinutes: number;
  clientAmount?: number;
}

export async function getOfficialPrice(stationId: string, durationMinutes: number) {
  const stationSpecific = await prisma.pricingOption.findFirst({
    where: {
      stationId,
      durationMinutes,
      enabled: true
    }
  });

  if (stationSpecific) {
    return stationSpecific;
  }

  const globalOption = await prisma.pricingOption.findFirst({
    where: {
      stationId: null,
      durationMinutes,
      enabled: true
    }
  });

  if (!globalOption) {
    if (durationMinutes === FIXED_PAYMENT_DURATION_MINUTES) {
      return {
        amount: new Prisma.Decimal(FIXED_PAYMENT_AMOUNT),
        durationMinutes: FIXED_PAYMENT_DURATION_MINUTES,
        label: "20 MIN"
      };
    }
    throw new HttpError(400, "Tempo nao disponivel para esta estacao.");
  }

  return globalOption;
}

function addMinutes(base: Date, minutes: number): Date {
  return new Date(base.getTime() + minutes * 60_000);
}

function isPendingExpired(createdAt: Date, now = new Date()): boolean {
  return addMinutes(createdAt, PENDING_PAYMENT_TTL_MINUTES) <= now;
}

function pendingPaymentResponse(session: {
  id: string;
  durationMinutes: number;
  amount: Prisma.Decimal;
  payment: {
    providerPaymentId: string;
    qrCode: string;
    pixCopiaECola: string;
  };
}) {
  return {
    sessionId: session.id,
    paymentId: session.payment.providerPaymentId,
    qrCode: session.payment.qrCode,
    pixCopiaECola: session.payment.pixCopiaECola,
    status: "PENDING" as const,
    amount: moneyToNumber(session.amount),
    durationMinutes: session.durationMinutes
  };
}

export async function createSessionPayment(input: CreateSessionPaymentInput) {
  if (input.durationMinutes !== FIXED_PAYMENT_DURATION_MINUTES) {
    throw new HttpError(400, `Somente ${FIXED_PAYMENT_DURATION_MINUTES} minutos estao disponiveis nesta estacao.`);
  }

  const station = await prisma.station.findUnique({ where: { id: input.stationId } });
  if (!station || !station.isActive) {
    throw new HttpError(404, "Estacao nao encontrada ou inativa.");
  }

  const activeSession = await prisma.session.findFirst({
    where: {
      stationId: input.stationId,
      status: {
        in: [SessionStatus.PENDING, SessionStatus.PAID, SessionStatus.ACTIVE]
      }
    },
    include: {
      payment: true
    },
    orderBy: {
      createdAt: "desc"
    }
  });

  if (activeSession) {
    if (activeSession.status === SessionStatus.PENDING) {
      if (!isPendingExpired(activeSession.createdAt) && activeSession.payment) {
        return pendingPaymentResponse({
          id: activeSession.id,
          durationMinutes: activeSession.durationMinutes,
          amount: activeSession.amount,
          payment: {
            providerPaymentId: activeSession.payment.providerPaymentId,
            qrCode: activeSession.payment.qrCode,
            pixCopiaECola: activeSession.payment.pixCopiaECola
          }
        });
      }

      await prisma.$transaction([
        prisma.payment.updateMany({
          where: { sessionId: activeSession.id },
          data: { status: "EXPIRED" }
        }),
        prisma.session.update({
          where: { id: activeSession.id },
          data: { status: SessionStatus.EXPIRED }
        }),
        prisma.auditLog.create({
          data: {
            stationId: activeSession.stationId,
            actor: "system",
            action: "SESSION_PENDING_EXPIRED",
            payload: {
              sessionId: activeSession.id,
              ttlMinutes: PENDING_PAYMENT_TTL_MINUTES
            }
          }
        })
      ]);
    } else if (!activeSession.expiresAt || activeSession.expiresAt > new Date()) {
      throw new HttpError(409, "Ja existe uma sessao ativa nesta estacao.");
    }
  }

  const officialPrice = await getOfficialPrice(input.stationId, input.durationMinutes);
  const officialAmount = moneyToNumber(officialPrice.amount);

  if (input.clientAmount !== undefined && Math.abs(input.clientAmount - officialAmount) > 0.001) {
    throw new HttpError(400, "Valor invalido. O preco oficial difere do enviado.");
  }

  const session = await prisma.session.create({
    data: {
      stationId: station.id,
      durationMinutes: input.durationMinutes,
      amount: new Prisma.Decimal(officialAmount),
      status: SessionStatus.PENDING
    }
  });

  const provider = getPaymentProvider();
  const payment = await provider.createPayment({
    sessionId: session.id,
    stationId: station.id,
    durationMinutes: input.durationMinutes,
    amount: officialAmount
  });

  await prisma.payment.create({
    data: {
      sessionId: session.id,
      provider: provider.name,
      providerPaymentId: payment.providerPaymentId,
      pixCopiaECola: payment.pixCopiaECola,
      qrCode: payment.qrCode,
      status: payment.status,
      rawPayload: payment.rawPayload as Prisma.InputJsonValue | undefined
    }
  });

  await prisma.auditLog.create({
    data: {
      stationId: station.id,
      actor: "station",
      action: "SESSION_PAYMENT_CREATED",
      payload: {
        sessionId: session.id,
        durationMinutes: input.durationMinutes,
        amount: officialAmount,
        provider: provider.name
      }
    }
  });

  return {
    sessionId: session.id,
    paymentId: payment.providerPaymentId,
    qrCode: payment.qrCode,
    pixCopiaECola: payment.pixCopiaECola,
    status: "PENDING" as const,
    amount: officialAmount,
    durationMinutes: session.durationMinutes
  };
}

export async function markSessionPaidByProviderPaymentId(providerPaymentId: string, paidAt = new Date()) {
  const payment = await prisma.payment.findFirst({
    where: { providerPaymentId },
    include: { session: true }
  });

  if (!payment) {
    throw new HttpError(404, "Pagamento nao encontrado.");
  }

  const session = payment.session;
  const expiresAt = addMinutes(paidAt, session.durationMinutes);

  await prisma.$transaction([
    prisma.payment.update({
      where: { id: payment.id },
      data: {
        status: "PAID",
        updatedAt: new Date()
      }
    }),
    prisma.session.update({
      where: { id: session.id },
      data: {
        status: SessionStatus.PAID,
        paidAt,
        startedAt: paidAt,
        expiresAt
      }
    }),
    prisma.auditLog.create({
      data: {
        stationId: session.stationId,
        actor: "provider",
        action: "SESSION_PAID",
        payload: {
          sessionId: session.id,
          providerPaymentId,
          paidAt: paidAt.toISOString(),
          expiresAt: expiresAt.toISOString()
        }
      }
    })
  ]);

  return {
    sessionId: session.id,
    expiresAt
  };
}

async function autoExpireIfNeeded(sessionId: string) {
  const session = await prisma.session.findUnique({ where: { id: sessionId } });
  if (!session) {
    throw new HttpError(404, "Sessao nao encontrada.");
  }

  if (session.status === SessionStatus.PENDING && isPendingExpired(session.createdAt)) {
    await prisma.$transaction([
      prisma.payment.updateMany({
        where: { sessionId: session.id },
        data: { status: "EXPIRED" }
      }),
      prisma.session.update({
        where: { id: session.id },
        data: { status: SessionStatus.EXPIRED }
      }),
      prisma.auditLog.create({
        data: {
          stationId: session.stationId,
          actor: "system",
          action: "SESSION_PENDING_EXPIRED",
          payload: {
            sessionId: session.id,
            ttlMinutes: PENDING_PAYMENT_TTL_MINUTES
          }
        }
      })
    ]);
    return;
  }

  if (
    session.expiresAt &&
    session.expiresAt <= new Date() &&
    (session.status === SessionStatus.PAID || session.status === SessionStatus.ACTIVE)
  ) {
    await prisma.session.update({
      where: { id: session.id },
      data: {
        status: SessionStatus.EXPIRED
      }
    });

    await prisma.auditLog.create({
      data: {
        stationId: session.stationId,
        actor: "system",
        action: "SESSION_AUTO_EXPIRED",
        payload: {
          sessionId: session.id
        }
      }
    });
  }
}

async function applyProviderStatusToSession(
  session: {
    id: string;
    stationId: string;
    payment: { providerPaymentId: string } | null;
  },
  providerStatus: ProviderPaymentStatus
) {
  if (!session.payment) return;

  if (providerStatus === "PAID") {
    await markSessionPaidByProviderPaymentId(session.payment.providerPaymentId, new Date());
    return;
  }

  if (providerStatus === "PENDING") return;

  const nextSessionStatus = providerStatus === "EXPIRED" ? SessionStatus.EXPIRED : SessionStatus.CANCELLED;

  await prisma.$transaction([
    prisma.payment.updateMany({
      where: { sessionId: session.id },
      data: {
        status: providerStatus
      }
    }),
    prisma.session.update({
      where: { id: session.id },
      data: {
        status: nextSessionStatus
      }
    }),
    prisma.auditLog.create({
      data: {
        stationId: session.stationId,
        actor: "provider",
        action: "SESSION_PROVIDER_STATUS_SYNC",
        payload: {
          sessionId: session.id,
          providerPaymentId: session.payment.providerPaymentId,
          providerStatus
        }
      }
    })
  ]);
}

export async function getSessionStatus(sessionId: string, stationId: string) {
  await autoExpireIfNeeded(sessionId);

  let session = await prisma.session.findFirst({
    where: {
      id: sessionId,
      stationId
    },
    include: {
      payment: true
    }
  });

  if (!session) {
    throw new HttpError(404, "Sessao nao encontrada para esta estacao.");
  }

  if (session.status === SessionStatus.PENDING && session.payment) {
    const provider = getPaymentProvider();

    try {
      const providerStatus = await provider.syncPaymentStatus?.({
        providerPaymentId: session.payment.providerPaymentId
      });

      if (providerStatus && providerStatus !== "PENDING") {
        await applyProviderStatusToSession(
          {
            id: session.id,
            stationId: session.stationId,
            payment: session.payment
          },
          providerStatus
        );

        session = await prisma.session.findFirst({
          where: {
            id: sessionId,
            stationId
          },
          include: {
            payment: true
          }
        });

        if (!session) {
          throw new HttpError(404, "Sessao nao encontrada para esta estacao.");
        }
      }
    } catch {
      // Falha de sincronizacao externa nao deve derrubar o polling da TV.
    }
  }

  if (!session) {
    throw new HttpError(404, "Sessao nao encontrada para esta estacao.");
  }

  return {
    sessionId: session.id,
    status: session.status,
    durationMinutes: session.durationMinutes,
    paidAt: session.paidAt,
    startedAt: session.startedAt,
    expiresAt: session.expiresAt,
    payment: session.payment
      ? {
          providerPaymentId: session.payment.providerPaymentId,
          status: session.payment.status
        }
      : null
  };
}

export async function forceUnlockStation(stationId: string, durationMinutes: number, actor: string) {
  const station = await prisma.station.findUnique({ where: { id: stationId } });
  if (!station) {
    throw new HttpError(404, "Estacao nao encontrada.");
  }

  const startedAt = new Date();
  const expiresAt = addMinutes(startedAt, durationMinutes);

  const session = await prisma.session.create({
    data: {
      stationId,
      durationMinutes,
      amount: new Prisma.Decimal(0),
      status: SessionStatus.ACTIVE,
      paidAt: startedAt,
      startedAt,
      expiresAt
    }
  });

  await prisma.auditLog.create({
    data: {
      stationId,
      actor,
      action: "FORCE_UNLOCK",
      payload: {
        sessionId: session.id,
        durationMinutes,
        expiresAt: expiresAt.toISOString()
      }
    }
  });

  return session;
}

export async function endSession(sessionId: string, actor: string) {
  const session = await prisma.session.findUnique({ where: { id: sessionId } });
  if (!session) {
    throw new HttpError(404, "Sessao nao encontrada.");
  }

  const updated = await prisma.session.update({
    where: { id: sessionId },
    data: {
      status: SessionStatus.EXPIRED,
      expiresAt: new Date()
    }
  });

  await prisma.auditLog.create({
    data: {
      stationId: session.stationId,
      actor,
      action: "SESSION_ENDED_MANUALLY",
      payload: {
        sessionId
      }
    }
  });

  return updated;
}
