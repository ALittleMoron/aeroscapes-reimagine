import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import { parse, type DefaultTreeAdapterMap } from "parse5";

export interface PublicPage {
  id: string;
  data: { status?: unknown };
}

export function validatePagePairs(pages: readonly PublicPage[]): void {
  if (pages.length === 0)
    throw new Error("No public documentation pages found");
  const byId = new Map(pages.map((page) => [page.id, page]));
  for (const page of pages) {
    const match = /^(en|ru)(?:\/(.+))?$/.exec(page.id);
    if (!match) throw new Error(`Unexpected public page locale: ${page.id}`);
    const partnerId = `${match[1] === "en" ? "ru" : "en"}${match[2] ? `/${match[2]}` : ""}`;
    const partner = byId.get(partnerId);
    if (!partner) throw new Error(`Missing language pair: ${partnerId}`);
    if (page.data.status !== partner.data.status)
      throw new Error(
        `Different implementation status: ${page.id}, ${partnerId}`,
      );
  }
}

interface HtmlDocument {
  ids: Set<string>;
  links: string[];
}

export function readHtml(html: string): HtmlDocument {
  const document: HtmlDocument = { ids: new Set(), links: [] };
  function visit(node: DefaultTreeAdapterMap["node"]): void {
    if ("attrs" in node) {
      for (const attribute of node.attrs) {
        if (attribute.name === "id") document.ids.add(attribute.value);
        if (attribute.name === "href") document.links.push(attribute.value);
      }
    }
    if ("childNodes" in node) node.childNodes.forEach(visit);
  }
  visit(parse(html));
  return document;
}

async function filesUnder(directory: string): Promise<string[]> {
  const entries = await readdir(directory, { withFileTypes: true });
  const groups = await Promise.all(
    entries.map(async (entry) => {
      const filename = path.join(directory, entry.name);
      return entry.isDirectory() ? filesUnder(filename) : [filename];
    }),
  );
  return groups.flat();
}

export async function validateBuiltLinks(
  directory: string,
  base: string,
): Promise<number> {
  const segments = base.split("/").filter(Boolean);
  const prefix = segments.length ? `/${segments.join("/")}/` : "/";
  const filenames = await filesUnder(directory);
  const available = new Set(
    filenames.map((filename) => path.relative(directory, filename)),
  );
  const documents = new Map<string, HtmlDocument>();
  for (const filename of filenames.filter((file) => file.endsWith(".html"))) {
    documents.set(
      path.relative(directory, filename),
      readHtml(await readFile(filename, "utf8")),
    );
  }
  let checked = 0;
  for (const [filename, document] of documents) {
    const route = filename.endsWith("index.html")
      ? filename.slice(0, -10)
      : filename;
    const source = new URL(`${prefix}${route}`, "https://docs.invalid");
    for (const href of document.links) {
      const target = new URL(href, source);
      if (target.origin !== source.origin) continue;
      if (!target.pathname.startsWith(prefix))
        throw new Error(`Link outside site base in ${filename}: ${href}`);
      const relative = decodeURIComponent(target.pathname.slice(prefix.length));
      const targetFile =
        relative.endsWith("/") || relative === ""
          ? `${relative}index.html`
          : relative;
      if (!available.has(targetFile))
        throw new Error(`Broken link in ${filename}: ${href}`);
      if (target.hash && documents.has(targetFile)) {
        const fragment = decodeURIComponent(target.hash.slice(1));
        if (!documents.get(targetFile)?.ids.has(fragment))
          throw new Error(`Broken anchor in ${filename}: ${href}`);
      }
      checked += 1;
    }
  }
  return checked;
}
