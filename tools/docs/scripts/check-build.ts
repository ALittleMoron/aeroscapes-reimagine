import { validateBuiltLinks } from "../src/validation.ts";

const count = await validateBuiltLinks(
  "dist",
  process.env.DOCS_BASE ?? "/aeroscapes-reimagine/",
);
console.log(`Documentation: ${count} local links and anchors checked.`);
