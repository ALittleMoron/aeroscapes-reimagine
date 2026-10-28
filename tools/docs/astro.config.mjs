import { defineConfig } from "astro/config";
import starlight from "@astrojs/starlight";

export default defineConfig({
  site: process.env.DOCS_SITE ?? "http://localhost:4321",
  base: process.env.DOCS_BASE ?? "/aeroscapes-reimagine/",
  trailingSlash: "always",
  vite: {
    resolve: {
      // Content lives outside this project's node_modules ancestry.
      dedupe: ["@astrojs/starlight"],
    },
  },
  integrations: [
    starlight({
      title: "Aeroscapes Reimagine",
      defaultLocale: "en",
      locales: {
        en: { label: "English", lang: "en" },
        ru: { label: "Русский", lang: "ru" },
      },
      disable404Route: true,
      customCss: ["./src/styles/custom.css"],
      head: [
        {
          tag: "meta",
          attrs: { name: "robots", content: "noindex, nofollow" },
        },
      ],
      components: {
        PageTitle: "./src/components/PageTitle.astro",
        LanguageSelect: "./src/components/LanguageSelect.astro",
      },
      sidebar: [
        {
          label: "About the pack",
          translations: { ru: "О сборке" },
          items: [{ slug: "" }, { slug: "vision" }, { slug: "gallery" }],
        },
        {
          label: "Releases",
          translations: { ru: "Релизы" },
          items: [
            { slug: "releases" },
            { slug: "changelog" },
            { slug: "install" },
          ],
        },
        {
          label: "The experience",
          translations: { ru: "Игровые направления" },
          items: [
            {
              label: "World and exploration",
              translations: { ru: "Мир и исследование" },
              collapsed: true,
              items: [
                {
                  slug: "experience/world",
                  label: "Overview",
                  translations: { ru: "Обзор" },
                },
                { slug: "experience/world/discoveries" },
                { slug: "experience/world/generation" },
              ],
            },
            {
              label: "Engineering and flight",
              translations: { ru: "Инженерия и полёты" },
              collapsed: true,
              items: [
                {
                  slug: "experience/engineering",
                  label: "Overview",
                  translations: { ru: "Обзор" },
                },
                { slug: "experience/engineering/mods" },
              ],
            },
            {
              label: "Character and combat",
              translations: { ru: "Персонаж и бой" },
              collapsed: true,
              items: [
                {
                  slug: "experience/character-and-combat",
                  label: "Overview",
                  translations: { ru: "Обзор" },
                },
              ],
            },
            {
              label: "Magic",
              translations: { ru: "Магия" },
              collapsed: true,
              items: [
                {
                  slug: "experience/magic",
                  label: "Overview",
                  translations: { ru: "Обзор" },
                },
              ],
            },
            {
              label: "Settlements",
              translations: { ru: "Поселения" },
              collapsed: true,
              items: [
                {
                  slug: "experience/settlements",
                  label: "Overview",
                  translations: { ru: "Обзор" },
                },
              ],
            },
            {
              label: "Building and daily life",
              translations: { ru: "Строительство и быт" },
              collapsed: true,
              items: [
                {
                  slug: "experience/building-and-daily-life",
                  label: "Overview",
                  translations: { ru: "Обзор" },
                },
                { slug: "experience/building-and-daily-life/food" },
              ],
            },
            {
              label: "Convenience",
              translations: { ru: "Удобство игры" },
              collapsed: true,
              items: [
                {
                  slug: "experience/quality-of-life",
                  label: "Overview",
                  translations: { ru: "Обзор" },
                },
                { slug: "experience/quality-of-life/presentation" },
              ],
            },
          ],
        },
        {
          label: "Details",
          translations: { ru: "Подробнее" },
          items: [
            { slug: "reference/mods" },
            { slug: "known-issues" },
            { slug: "credits" },
          ],
        },
      ],
    }),
  ],
});
