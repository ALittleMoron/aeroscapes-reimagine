import assert from "node:assert/strict";
import { mkdtemp, mkdir, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { test } from "node:test";
import { validateBuiltLinks, validatePagePairs } from "../src/validation.ts";

test("a missing language or different availability blocks publication", () => {
  const english = { id: "en/experience/world", data: { status: "partial" } };
  assert.throws(() => validatePagePairs([english]), /Missing language pair/);
  const russian = { id: "ru/experience/world", data: { status: "planned" } };
  assert.throws(
    () => validatePagePairs([english, russian]),
    /Different implementation status/,
  );
  russian.data.status = "partial";
  assert.doesNotThrow(() => validatePagePairs([english, russian]));
  assert.doesNotThrow(() =>
    validatePagePairs([
      { id: "en", data: { status: "partial" } },
      { id: "ru", data: { status: "partial" } },
    ]),
  );
});

test("built links resolve relative routes, UTF-8 fragments and project prefixes", async (context) => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "aeroscapes-docs-"));
  context.after(() => rm(directory, { recursive: true, force: true }));
  await mkdir(path.join(directory, "ru", "world"), { recursive: true });
  await writeFile(
    path.join(directory, "ru", "index.html"),
    '<a href="world/#%D0%BC%D0%B8%D1%80">World</a><a href="mailto:a@example.com">Email</a>',
  );
  const page = path.join(directory, "ru", "world", "index.html");
  await writeFile(page, '<h1 id="мир">World</h1><a href="../">Home</a>');
  assert.equal(await validateBuiltLinks(directory, "/pack/"), 2);
  await writeFile(
    page,
    '<h1 id="мир">World</h1><a href="missing/">Missing</a>',
  );
  await assert.rejects(validateBuiltLinks(directory, "/pack/"), /Broken link/);
  await writeFile(
    page,
    '<h1 id="мир">World</h1><a href="#missing">Missing anchor</a>',
  );
  await assert.rejects(
    validateBuiltLinks(directory, "/pack/"),
    /Broken anchor/,
  );
  await writeFile(page, '<h1 id="мир">World</h1><a href="/ru/">Wrong base</a>');
  await assert.rejects(
    validateBuiltLinks(directory, "/pack/"),
    /outside site base/,
  );
});
