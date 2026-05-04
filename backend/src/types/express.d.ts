import type { Station } from "@prisma/client";

declare global {
  namespace Express {
    interface Request {
      station?: Station;
    }
  }
}

export {};
