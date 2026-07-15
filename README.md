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
- Craft a permanent Network Wand from two sticks and redstone. Use it in air to cycle between Add to Network, Set Network Source, and Configure Storage modes; sneak-use it in air to manage networks.
- Link storage to one network at a time, name it, assign numeric priorities, choose exactly one origin block for future remote lookup, and manage public/private membership.
- Normal clicks and shift-clicks only insert into the opened storage and obey that storage's label filter; they do not route across the network. Explicit network actions such as safe deposit, the terminal, refills, and input/output ports still use network-wide routing and priorities.
- Craft a Network Interface from a crafting table, four ender pearls, and four crying obsidian. Every linked interface within 256 blocks on each axis of the network source can open the paged crafting terminal.
- The terminal browses the combined loaded inventory and crafts in a 3×3 grid. Recipe-transfer helpers use live physical network slots, so whole and partial ingredient transfers remove their backing items even when ingredients are off-page.
- Craft dedicated Network Input and Network Output blocks from an interface, hopper, and blue or orange dye. Machines can only push into the whole network through an Input and pull from it through an Output; linked chests and interfaces no longer expose network-wide automation.
- Within 256 blocks on each axis of any linked storage, a member automatically receives a replacement stack or tool after the last inventory replacement is consumed or broken. The network origin is not used for refills.
- Configurable keybinds provide item, category, and quantity sorting plus consolidate, transfer-matching, and safe-deposit actions while an inventory or container screen is open. Sorting targets the inventory section under the cursor, including the player inventory, hotbar, vanilla containers, and compatible modded item-handler inventories. Safe deposit keeps the hotbar, armour, favourite item IDs, and locked slots.
- Configure the unbound Mobs Storage controls under Options > Controls > Key Binds. Hover a player slot and use the configured lock or favourite key; locked and favourite slots are also protected from shift-click quick deposit.
- Hover a hotbar slot and use the configured preference key to remember its current item, or use the network-restock key over any player slot. Slot markers show the active rules and name a nearby network that can currently supply a configured item.
- Broken tools check compatible carried item-handler storage for the same registered item ID before falling back to network refill, so durability and other components do not prevent replacement.

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
