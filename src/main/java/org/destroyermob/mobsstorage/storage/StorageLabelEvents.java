package org.destroyermob.mobsstorage.storage;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.destroyermob.mobsstorage.network.ModNetworking;
import org.destroyermob.mobsstorage.registry.ModItems;
import org.destroyermob.mobsstorage.networking.NetworkService;

public final class StorageLabelEvents {
    private StorageLabelEvents() {
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()) {
            return;
        }
        Player player = event.getEntity();
        Level level = event.getLevel();
        if (!player.isShiftKeyDown() || !StorageResolver.eligible(level, event.getPos())) {
            return;
        }

        ItemStack held = player.getItemInHand(event.getHand());
        Optional<LabelData> existing = StorageResolver.findLabel(level, event.getPos());
        boolean install = held.is(ModItems.STORAGE_LABEL.get()) && existing.isEmpty();
        boolean edit = held.isEmpty() && existing.isPresent();
        boolean remove = held.is(Items.SHEARS) && existing.isPresent();
        if (!install && !edit && !remove) {
            return;
        }

        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        event.setCanceled(true);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(event.getPos());
        if (blockEntity instanceof BaseContainerBlockEntity container && !container.canOpen(player)) {
            return;
        }

        if (remove) {
            removeLabel(serverPlayer, event.getPos(), held, event.getHand());
            return;
        }

        LabelData initial = existing.orElse(new LabelData(
                LabelData.AIR,
                List.of(),
                event.getFace(),
                LabelDisplayMode.CROSSHAIR,
                false,
                event.getPos().immutable()
        ));
        ModNetworking.openEditor(serverPlayer, event.getPos(), initial, install);
    }

    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        scheduleTopologyReconciliation(level, event.getPos());
        NetworkService.onStorageBroken(level, event.getPos());
        Optional<LabelData> label = StorageResolver.findLabel(level, event.getPos());
        if (label.isEmpty() || !label.get().anchor().equals(event.getPos())) {
            return;
        }
        StorageResolver.clearLabel(level, StorageResolver.logicalStorage(level, event.getPos()));
        if (!event.getPlayer().getAbilities().instabuild) {
            Block.popResource(level, event.getPos(), new ItemStack(ModItems.STORAGE_LABEL.get()));
        }
    }

    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        scheduleTopologyReconciliation(level, event.getPos());
    }

    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        boolean storageNearby = StorageResolver.networkEligible(level, event.getPos());
        if (!storageNearby) {
            for (Direction direction : Direction.values()) {
                if (StorageResolver.networkEligible(level, event.getPos().relative(direction))) {
                    storageNearby = true;
                    break;
                }
            }
        }
        if (!storageNearby) {
            return;
        }
        scheduleTopologyReconciliation(level, event.getPos());
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        level.getServer().execute(() -> List.copyOf(chunk.getBlockEntities().keySet()).forEach(pos -> {
            if (level.isLoaded(pos) && StorageResolver.networkEligible(level, pos)) {
                reconcileAt(level, pos);
            }
        }));
    }

    private static void scheduleTopologyReconciliation(ServerLevel level, BlockPos changedPos) {
        BlockPos center = changedPos.immutable();
        level.getServer().execute(() -> {
            reconcileAt(level, center);
            for (Direction direction : Direction.values()) {
                reconcileAt(level, center.relative(direction));
            }
        });
    }

    private static void reconcileAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !StorageResolver.networkEligible(level, pos)) {
            return;
        }
        if (StorageResolver.eligible(level, pos)) {
            List<BlockEntity> storage = StorageResolver.logicalStorage(level, pos);
            storage.stream().map(StorageResolver::existingLabel).flatMap(Optional::stream).findFirst()
                    .ifPresent(label -> StorageResolver.setLabel(level, storage, label));
            StorageResolver.reconcileLabelTopology(level, pos);
        }
        NetworkService.onStorageJoined(level, pos);
        NetworkService.reconcileTopology(level, pos);
    }

    private static void removeLabel(ServerPlayer player, BlockPos pos, ItemStack shears, net.minecraft.world.InteractionHand hand) {
        ServerLevel level = player.serverLevel();
        List<BlockEntity> storage = StorageResolver.logicalStorage(level, pos);
        if (storage.stream().noneMatch(blockEntity -> StorageResolver.existingLabel(blockEntity).isPresent())) {
            return;
        }
        org.destroyermob.mobsstorage.networking.NetworkNodeData node = storage.stream()
                .map(NetworkService::nodeData).findFirst()
                .orElse(org.destroyermob.mobsstorage.networking.NetworkNodeData.EMPTY);
        StorageResolver.clearLabel(level, storage);
        NetworkService.updateDetails(level, pos, node.name(), node.priority(), LabelData.AIR);
        if (!player.getAbilities().instabuild) {
            ItemStack label = new ItemStack(ModItems.STORAGE_LABEL.get());
            if (!player.addItem(label)) {
                player.drop(label, false);
            }
            shears.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
    }
}
