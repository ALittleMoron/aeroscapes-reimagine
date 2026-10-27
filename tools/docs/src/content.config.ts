import { defineCollection } from "astro:content";
import { z } from "astro/zod";
import { glob } from "astro/loaders";
import { docsSchema, i18nSchema } from "@astrojs/starlight/schema";
import { i18nLoader } from "@astrojs/starlight/loaders";
import { validatePagePairs } from "./validation.ts";

const publicDocs = glob({
  base: "../../docs",
  pattern: "{en,ru}/**/*.{md,mdx}",
});

export const collections = {
  i18n: defineCollection({ loader: i18nLoader(), schema: i18nSchema() }),
  docs: defineCollection({
    loader: {
      ...publicDocs,
      name: "paired-public-docs",
      async load(context) {
        await publicDocs.load(context);
        validatePagePairs(Array.from(context.store.values()));
      },
    },
    schema: docsSchema({
      extend: z.object({ status: z.enum(["available", "partial", "planned"]) }),
    }),
  }),
};
