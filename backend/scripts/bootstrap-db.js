const crypto = require("crypto");
const { PrismaClient } = require("@prisma/client");

const prisma = new PrismaClient();

function hashStationToken(rawToken, salt) {
  return crypto.createHash("sha256").update(`${salt}:${rawToken}`).digest("hex");
}

async function main() {
  const stationId = process.env.STATION_ID || "tv-01";
  const stationName = process.env.STATION_NAME || "TV 01 - PS5";
  const stationToken = process.env.STATION_TOKEN || "tv01-secret-token";
  const stationTokenSalt = process.env.STATION_TOKEN_SALT || "local-dev-salt";
  const price20 = Number(process.env.PRICE_20 || "15");

  await prisma.station.upsert({
    where: { id: stationId },
    create: {
      id: stationId,
      name: stationName,
      stationTokenHash: hashStationToken(stationToken, stationTokenSalt),
      isActive: true
    },
    update: {
      name: stationName,
      stationTokenHash: hashStationToken(stationToken, stationTokenSalt),
      isActive: true
    }
  });

  const existing20 = await prisma.pricingOption.findFirst({
    where: {
      stationId: null,
      durationMinutes: 20
    }
  });

  if (existing20) {
    await prisma.pricingOption.update({
      where: { id: existing20.id },
      data: {
        label: "20 MIN",
        amount: price20,
        enabled: true
      }
    });
  } else {
    await prisma.pricingOption.create({
      data: {
        stationId: null,
        label: "20 MIN",
        durationMinutes: 20,
        amount: price20,
        enabled: true
      }
    });
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

  console.log("Database bootstrap concluido.");
  console.log(`Station ID: ${stationId}`);
  console.log(`Station token: ${stationToken}`);
}

main()
  .catch((error) => {
    console.error(error);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
