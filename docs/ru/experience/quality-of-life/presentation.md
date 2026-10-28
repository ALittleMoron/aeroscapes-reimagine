---
title: Графика, звук и анимации
description: Отображение персонажа, окружающие звуки, визуальные детали и выбор шейдеров.
status: available
---

Более подробные анимации, звуки окружения и небольшие визуальные эффекты дополняют стандартное оформление Minecraft.

## Персонаж и камера

[Not Enough Animations](https://modrinth.com/project/MPCX6s5C) добавляет и улучшает анимации от третьего лица. 3D Skin Layers придаёт объём внешнему слою скина. От первого лица используется обычный вид с руками: отображение всего анимированного тела могло перекрывать обзор в полёте.

[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension) добавляет персонажу более выразительные движения, позы и анимации рук. Для него используются те же Entity Model Features и Entity Texture Features, что и для Fresh Animations у существ.

Эти моды меняют отображение существующего персонажа. Расы, способности, расширенный экшен-бой и смещённая вбок камера от третьего лица описаны в разделе [«Персонаж и бой»](../../character-and-combat/).

## Существа, листва и механизмы

[Fresh Animations](https://modrinth.com/resourcepack/fresh-animations) делает движения и позы существ выразительнее. Entity Model Features и Entity Texture Features обеспечивают его работу на клиенте. Анимации самого персонажа остаются отдельным слоем.

[Motschen’s Better Leaves](https://modrinth.com/resourcepack/better-leaves) придаёт кронам более пышный силуэт. Он охватывает ванильную листву, листву Quark, большинство видов Nature’s Spirit и варианты листвы Chipped. Кокосовая листва Nature’s Spirit сохраняет исходную модель. Меняется вид листьев, а не генерация деревьев.

[Falling Leaves](https://modrinth.com/mod/fallingleavesforge) добавляет частицы падающих листьев. У вишни сохраняются существующие частицы, а лепестки цветущей азалии остаются за Subtle Effects, чтобы на этих блоках не появлялся второй фоновый эффект листвы.

[Create Touch-Ups](https://modrinth.com/resourcepack/create-touch-ups) приближает модели механизмов, инструментов и текстуры меди Create к более простому ванильному стилю.

## Стекло и переносной свет

[Fusion Connected Glass](https://modrinth.com/resourcepack/fusion-connected-glass) с поддержкой Fusion соединяет текстуры соседних ванильных блоков и панелей стекла. Поддерживаются цветное и тонированное стекло; стекло других модов автоматически не меняется.

[LambDynamicLights](https://modrinth.com/mod/lambdynamiclights) создаёт клиентское освещение от источников света в руках. [Extended Illumina NEA](https://modrinth.com/resourcepack/extended-illumina-not-enough-animations) придаёт удерживаемым факелам и фонарям объёмные модели и меняет их положение. Модели и освещение — отдельные возможности: отключение ресурспака не отключает LambDynamicLights.

Эти ресурспаки и небольшой пакет интеграции атмосферы выбраны в новых установках. Каждый можно отключить в **Options → Resource Packs**. Для включённых Fresh Animations и Player Extension нужны оба мода EMF и ETF. В существующих профилях сохраняются выбранные пакеты и настройки.

## Звуки и визуальные детали

AmbientSounds добавляет звуки окружения, а Sounds расширяет звуки повседневных действий. Drip Sounds озвучивает капающие частицы. Subtle Effects добавляет небольшие детали в виде частиц и некоторые звуки в мире.

Smooth Skies сглаживает цвета далёкого неба, а Clear Void убирает чёрную нижнюю часть небесного фона. В интерфейсе Chat Animation анимирует чат, а Smooth Gui — открытие меню.

## Отрисовка

Sodium отвечает за отрисовку мира. ImmediatelyFast объединяет отрисовку в пакеты, Flerovium сокращает затраты на отрисовку моделей и частиц, а BadOptimizations повторно использует расчёты освещения и неба. Entity Culling и More Culling исключают из отрисовки скрытые объекты и грани блоков.

Vanillin использует Flywheel для отрисовки ванильных сундуков, шалкеровых ящиков и колоколов.

## Шейдеры

В сборку входят [Complementary Reimagined](https://modrinth.com/project/HVnmMxH1) и [MakeUp Ultra Fast](https://modrinth.com/project/izsIPI7a) с подготовленными начальными профилями. По умолчанию шейдеры отключены и остаются необязательными для игры.

Colorwheel связывает шейдеры Iris с отрисовкой Flywheel, которую используют Create и Vanillin.
