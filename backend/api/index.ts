import { buildApp } from "../src/app";

const app = buildApp();

export default app;

// CommonJS fallback for some Vercel runtime paths
module.exports = app;
