import { defineConfig } from "orval";

export default defineConfig({
  hittiguess: {
    input: {
      target: "http://localhost:8080/v3/api-docs",
    },
    output: {
      mode: "tags-split",
      target: "hooks/generated",
      schemas: "hooks/models",
      client: "react-query",
      mock: false,
      override: {
        mutator: {
          path: "lib/axios-instance.ts",
          name: "customInstance",
        },
      },
    },
  },
  hittiguessZod: {
    input: {
      target: "http://localhost:8080/v3/api-docs",
    },
    output: {
      mode: "tags-split",
      target: "hooks/zod",
      client: "zod",
    },
  },
});
