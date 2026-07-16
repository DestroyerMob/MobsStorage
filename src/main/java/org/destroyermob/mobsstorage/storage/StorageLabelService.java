package org.destroyermob.mobsstorage.storage;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.destroyermob.mobsstorage.network.SaveLabelPayload;
import org.destroyermob.mobsstorage.registry.ModItems;
import org.destroyermob.mobsstorage.networking.NetworkService;

public final class StorageLabelService {
    private static final double MAX_DISTANCE_SQUARED = 64.0D;

    private StorageLabelService() {
    }

    public static void save(ServerPlayer player, SaveLabelPayload payload) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = payload.pos();
        if (!level.isLoaded(pos)
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQUARED
                || !StorageResolver.eligible(level, pos)
                || FilterRules.validateIcon(payload.icon()).isPresent()
                || FilterRules.validate(payload.filters()).isPresent()) {
            return;
        }

        List<BlockEntity> storage = StorageResolver.logicalStorage(level, pos);
        Optional<LabelData> existing = storage.stream().map(StorageResolver::existingLabel).flatMap(Optional::stream).findFirst();
        if (existing.isEmpty() && !consumeLabel(player)) {
            return;
        }

        BlockPos anchor = existing.map(LabelData::anchor).filter(level::isLoaded).orElse(pos).immutable();
        LabelData data = new LabelData(
                payload.icon(), payload.filters(), payload.face(), payload.displayMode(), payload.alwaysShow(), anchor);
        StorageResolver.setLabel(level, storage, data);
        NetworkService.updateDetails(level, pos, payload.storageName(), payload.priority(), payload.icon());
        if (payload.ejectConflicts()) {
            ejectDisallowed(level, storage, data, player.position());
        }
    }

    private static boolean consumeLabel(ServerPlayer player) {
        for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.is(ModItems.STORAGE_LABEL.get())) {
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                return true;
            }
        }
        return false;
    }

    public static void ejectDisallowed(ServerLevel level, List<BlockEntity> storage, LabelData data, Vec3 dropAt) {
        for (BlockEntity blockEntity : storage) {
            if (!(blockEntity instanceof Container container)) {
                continue;
            }
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty() && !data.allows(stack, level)) {
                    ItemStack removed = container.removeItemNoUpdate(slot);
                    ItemEntity entity = new ItemEntity(level, dropAt.x, dropAt.y, dropAt.z, removed);
                    entity.setPickUpDelay(0);
                    level.addFreshEntity(entity);
                }
            }
            container.setChanged();
        }
    }
}
