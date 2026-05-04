import { PrismaClient } from "@prisma/client";
import { env } from "../src/config/env";
import { hashStationToken } from "../src/core/stationToken";

const prisma = new PrismaClient();

async function main() {
  const stationId = "tv-01";
  const stationToken = "tv01-secret-token";

  await prisma.station.upsert({
    where: { id: stationId },
    create: {
      id: stationId,
      name: "TV 01 - PS5",
      stationTokenHash: hashStationToken(stationToken, env.STATION_TOKEN_SALT),
      isActive: true
    },
    update: {
      name: "TV 01 - PS5",
      stationTokenHash: hashStationToken(stationToken, env.STATION_TOKEN_SALT),
      isActive: true
    }
  });

  const defaultPricing = [{ label: "20 MIN", durationMinutes: 20, amount: 15.0 }];

  for (const option of defaultPricing) {
    const existing = await prisma.pricingOption.findFirst({
      where: {
        stationId: null,
        durationMinutes: option.durationMinutes
      }
    });

    if (existing) {
      await prisma.pricingOption.update({
        where: { id: existing.id },
        data: {
          label: option.label,
          amount: option.amount,
          enabled: true
        }
      });
    } else {
      await prisma.pricingOption.create({
        data: {
          stationId: null,
          label: option.label,
          durationMinutes: option.durationMinutes,
          amount: option.amount,
          enabled: true
        }
      });
    }
  }

  await prisma.pricingOption.updateMany({
    where: {
      stationId: null,
      durationMinutes: {
        not: 20
      }
    },
    data: {
      enabled: false
    }
  });

  console.log("Seed concluído.");
  console.log(`Station ID: ${stationId}`);
  console.log(`Station token (plaintext para setup local): ${stationToken}`);
}

main()
  .catch((error) => {
    console.error(error);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
