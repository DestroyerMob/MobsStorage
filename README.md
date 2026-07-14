# Mobs Storage

Mobs Storage adds configurable storage labels to Minecraft 1.21.1 on NeoForge. A label displays a chosen item icon on a chest or other vanilla-style container and can optionally restrict insertion with item IDs and item tags.

## Current Features

- Craft four Storage Labels from one item frame and one name tag.
- Sneak-use a label on a chest, trapped chest, barrel, shulker box, or compatible vanilla-style chest.
- Pick any registered item icon, add exact item IDs or `#namespace:tag` whitelist entries, and choose whether the icon is always visible.
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
