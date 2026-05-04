import crypto from "crypto";

export function hashStationToken(rawToken: string, salt: string): string {
  return crypto
    .createHash("sha256")
    .update(`${salt}:${rawToken}`)
    .digest("hex");
}
