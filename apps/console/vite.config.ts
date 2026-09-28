import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { "/api": "http://localhost:8790" },
  },
  build: {
    outDir: "../../sre-investigator-boot/src/main/resources/static",
    emptyOutDir: true,
  },
});
