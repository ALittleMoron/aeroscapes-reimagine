---
title: Installation
description: Installing a supplied modpack file in Modrinth App.
status: partial
---

Import a downloaded or supplied `.mrpack` as a new instance in Modrinth App.

1. In Modrinth App 0.22.0, open the app settings → **Instance defaults → Java and memory**. Find **Java 25 location** in the Java installations list and click **Install recommended**. The launcher downloads Java for you; no separate system JDK installation is needed.
2. Import the `.mrpack` as a new instance and let it download the pack's files. The pack specifies Minecraft 1.21.1 and NeoForge.
3. Open this instance's settings → **Java and memory**, enable **Custom Java installation**, click **Detect** and select the entry with version **25**. Wait for a successful Java check. Installing Java 25 in the app settings alone is insufficient: this Minecraft version needs the instance override.
4. Start the instance and create a world using the normal world type. Menu locations may differ in other app versions.

The OpenCL generation module can use a compatible GPU and its OpenCL driver; otherwise generation can fall back to the CPU. Java 25 is required in either case. For existing worlds and updates, read [Compatibility and support](../known-issues/) and keep a backup before changing versions.
