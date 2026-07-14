# Mobs Storage

Mobs Storage adds configurable storage labels to Minecraft 1.21.1 on NeoForge. A label displays a chosen item icon on a chest or other vanilla-style container and can optionally restrict insertion with server-authoritative search expressions.

## Current Features

- Craft four Storage Labels from one item frame and one name tag.
- Sneak-use a label on a chest, trapped chest, barrel, shulker box, or compatible vanilla-style chest.
- Pick any registered item icon and display it mounted on the clicked face, as a camera-facing billboard, or beside the crosshair.
- Build whitelist rows from exact item IDs, `#tags`, `@modid`, `&resource-id`, `$tooltip`, or plain name/path searches. Space-separated terms are ANDed, `|` alternatives are ORed, and a leading `-` negates a term.
- Search and autocomplete registered item IDs, item tags, and mod namespaces directly in the editor.
- Rejected manual and automated insertion is silent. Applying a filter drops existing disallowed contents at the player's feet.
- One label covers both halves of a double chest.
- Empty-hand sneak-use edits a label; shears remove and return it.

Custom/networked storage systems such as Sophisticated Storage, Tom's Simple Storage, Ender Storage, and Lootr are intentionally outside the first compatibility slice.

## Project Facts

- Mod id: `mobsstorage`
- Version: `0.1.0`
- Minecraft: `1.21.1`
- NeoForge: `21.1.234`
- Java: `21`
- License: MIT

## Building

```bash
./gradlew build
```

The runtime jar is written to `build/libs/`.
