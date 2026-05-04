import { env } from "./config/env";
import { logger } from "./core/logger";
import { disconnectPrisma } from "./db/prisma";
import { buildApp } from "./app";

async function main() {
  const app = buildApp();

  const server = app.listen(env.PORT, () => {
    logger.info(`Backend rodando em http://localhost:${env.PORT}`);
  });

  const shutdown = async () => {
    logger.info("Encerrando servidor...");
    server.close(async () => {
      await disconnectPrisma();
      process.exit(0);
    });
  };

  process.on("SIGINT", shutdown);
  process.on("SIGTERM", shutdown);
}

main().catch(async (error) => {
  logger.error("Falha ao iniciar backend", error);
  await disconnectPrisma();
  process.exit(1);
});
