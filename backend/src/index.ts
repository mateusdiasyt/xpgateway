import { buildApp } from "./app";

const app = buildApp();

export default app;

// CommonJS fallback for Node runtimes that rely on module.exports
module.exports = app;
