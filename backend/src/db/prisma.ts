import { PrismaClient } from "@prisma/client";

let prismaClient: PrismaClient | null = null;
let prismaInitError: unknown = null;

function getClient(): PrismaClient {
  if (prismaClient) {
    return prismaClient;
  }

  if (prismaInitError) {
    throw prismaInitError;
  }

  try {
    prismaClient = new PrismaClient();
    return prismaClient;
  } catch (error) {
    prismaInitError = error;
    throw error;
  }
}

export const prisma = new Proxy({} as PrismaClient, {
  get(_target, prop, receiver) {
    const client = getClient();
    const value = Reflect.get(client as object, prop, receiver);
    if (typeof value === "function") {
      return value.bind(client);
    }
    return value;
  }
});

export async function getDatabaseHealth() {
  try {
    const client = getClient();
    await client.$queryRawUnsafe("SELECT 1");
    return { ok: true as const };
  } catch (error) {
    return {
      ok: false as const,
      message: error instanceof Error ? error.message : "Unknown database error"
    };
  }
}

export async function disconnectPrisma() {
  if (!prismaClient) {
    return;
  }
  await prismaClient.$disconnect();
}
