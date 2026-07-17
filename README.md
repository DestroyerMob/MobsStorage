# Mobs Storage

Mobs Storage adds configurable storage labels to Minecraft 1.21.1 on NeoForge. A label displays a chosen item icon on a chest or other vanilla-style container and can optionally restrict insertion with server-authoritative search expressions.

## Current Features

- Craft four Storage Labels from one item frame and one name tag.
- Sneak-use a label on a chest, trapped chest, barrel, shulker box, or compatible vanilla-style chest.
- Pick any registered item icon and display it mounted on the clicked face, as a camera-facing billboard, or beside the crosshair.
- Build whitelist rows from exact item IDs, `#tags`, `@modid`, `&resource-id`, `$tooltip`, or plain name/path searches. Space-separated terms are ANDed, `|` alternatives are ORed, and a leading `-` negates a term.
- Search and autocomplete registered item IDs, item tags, and mod namespaces directly in the editor.
- Rejected automation is silent. A rejected manual insert routes to another compatible chest on the same network; if none can accept it, the item stays with the player. Applying a filter drops existing disallowed contents at the player's feet.
- One label covers both halves of a double chest.
- Empty-hand sneak-use edits a label; shears remove and return it.
- Craft a permanent Network Wand from two sticks and redstone. Use it in air to cycle between Add to Network, Set Network Source, and Configure Storage modes; sneak-use it in air to manage networks.
- Link storage to one network at a time, name it, assign numeric priorities, choose exactly one origin block for future remote lookup, and manage public/private membership. The manager's Diagnostics tab reports live capacity and flags unloaded, missing, incorrectly linked, unavailable, or out-of-range nodes.
- Normal clicks and shift-clicks insert locally when the opened storage accepts the item. If its label rejects the item, the insertion is forwarded to compatible network storage using normal filter and priority rules.
- Craft a Network Interface from a crafting table, four ender pearls, and four crying obsidian. Every linked interface within 256 blocks on each axis of the network source can open the paged crafting terminal.
- The compact terminal browses six rows of the combined loaded inventory, crafts in a 3×3 grid, and filters inline with the label expression language. Left-side controls sort by item, quantity, or mod in either direction. Recipe-transfer helpers use live physical network slots, so whole and partial ingredient transfers remove their backing items even when ingredients are off-page.
- Craft dedicated Network Input and Network Output blocks shapelessly from a hopper, ender pearl, and blue or orange dye. Machines can only push into the whole network through an Input and pull from it through an Output; linked chests and interfaces no longer expose network-wide automation. Configure an Output directly or with the wand to restrict extraction with the same expression language used by labels.
- Within 256 blocks on each axis of any linked storage, a member automatically receives a replacement stack or tool after the last inventory replacement is consumed or broken. The network origin is not used for refills.
- Configurable keybinds provide item, category, and quantity sorting plus consolidate, transfer-matching, and safe-deposit actions while an inventory or container screen is open. Sorting targets the inventory section under the cursor, including the player inventory, hotbar, vanilla containers, and compatible modded item-handler inventories. Safe deposit keeps the hotbar, armour, favourite item IDs, and locked slots.
- Configure Mobs Storage controls under Options > Controls > Key Binds. Carry rules default to C; hover a player slot and press it to open that slot's editor. Hover a player slot and use the configured lock or favourite key; locked and favourite slots are also protected from shift-click quick deposit.
- Hold Ctrl and scroll to preview the selected hotbar column vertically through the three inventory rows, hold Ctrl+Shift and scroll to preview an entire inventory row as the next hotbar, or hold Alt/Option and scroll to choose another hotbar slot for a horizontal swap. The HUD shows the pending selection and performs one server-authoritative swap when the modifier chord is released.
- Hover a hotbar slot and use the configured preference key to remember its current item, or use the network-restock key over any player slot. Slot markers show the active rules and name a nearby network that can currently supply a configured item.
- Each player-inventory slot can have one carry rule. Hover it and press the Carry Rule key to open a compact, sharp popup beside the slot; choose an exact stack or label-language filter and set minimum, target, and maximum counts. Nearby networks refill that specific slot below minimum, while safe deposit keeps its configured maximum.
- Broken tools check compatible carried item-handler storage for the same registered item ID before falling back to network refill, so durability and other components do not prevent replacement.
- Hover a bundle in any open inventory and scroll to highlight a specific contained stack. Right-click the bundle with an empty cursor to extract the highlighted stack.
- Link capability-backed individual storage blocks to a network. Functional Storage drawers, compacting drawers, and armory cabinets are supported when installed; Sophisticated Storage chests, barrels, limited barrels, and shulker boxes are supported optionally without a hard dependency.

Storage controllers, controller extensions, Functional Storage fluid and Ender drawers, Tom's Simple Storage, Ender Storage, and Lootr are intentionally excluded so shared, recursive, or per-player inventories cannot be counted twice or bypass their ownership rules.

## Project Facts

- Mod id: `mobsstorage`
- Version: `0.3.0`
- Minecraft: `1.21.1`
- NeoForge: `21.1.234`
- Java: `21`
- License: MIT

## Building

```bash
./gradlew build
```

The runtime jar is written to `build/libs/`.
