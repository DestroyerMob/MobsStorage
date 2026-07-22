package org.destroyermob.mobsstorage.registry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.destroyermob.mobsstorage.MobsStorage;
import org.destroyermob.mobsstorage.inventory.InventoryProfile;
import org.destroyermob.mobsstorage.inventory.CarryRuleSet;
import org.destroyermob.mobsstorage.inventory.EquipmentLoadout;
import org.destroyermob.mobsstorage.networking.NetworkNodeData;
import org.destroyermob.mobsstorage.storage.LabelData;

public final class ModAttachments {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MobsStorage.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<LabelData>> STORAGE_LABEL =
            TYPES.register("storage_label", () -> AttachmentType.builder(() -> LabelData.EMPTY)
                    .serialize(LabelData.CODEC, LabelData::configured)
                    .sync(LabelData.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<NetworkNodeData>> NETWORK_NODE =
            TYPES.register("network_node", () -> AttachmentType.builder(() -> NetworkNodeData.EMPTY)
                    .serialize(NetworkNodeData.CODEC, NetworkNodeData::configured)
                    .sync(NetworkNodeData.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<InventoryProfile>> INVENTORY_PROFILE =
            TYPES.register("inventory_profile", () -> AttachmentType.builder(() -> InventoryProfile.EMPTY)
                    .serialize(InventoryProfile.CODEC, InventoryProfile::configured)
                    .copyOnDeath()
                    .sync((holder, player) -> holder == player, InventoryProfile.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CarryRuleSet>> CARRY_RULES =
            TYPES.register("carry_rules", () -> AttachmentType.builder(() -> CarryRuleSet.EMPTY)
                    .serialize(CarryRuleSet.CODEC, CarryRuleSet::configured)
                    .copyOnDeath()
                    .sync((holder, player) -> holder == player, CarryRuleSet.STREAM_CODEC)
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<EquipmentLoadout>> EQUIPMENT_LOADOUT =
            TYPES.register("equipment_loadout", () -> AttachmentType.builder(() -> EquipmentLoadout.EMPTY)
                    .serialize(EquipmentLoadout.CODEC, EquipmentLoadout::configured)
                    .copyOnDeath()
                    .sync((holder, player) -> holder == player, EquipmentLoadout.STREAM_CODEC)
                    .build());

    private ModAttachments() {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
