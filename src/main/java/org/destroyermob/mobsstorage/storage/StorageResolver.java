package org.destroyermob.mobsstorage.storage;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.destroyermob.mobsstorage.mixin.CompoundContainerAccessor;
import org.destroyermob.mobsstorage.registry.ModAttachments;
import org.destroyermob.mobsstorage.registry.ModTags;
import org.destroyermob.mobsstorage.world.NetworkInterfaceBlockEntity;
import org.destroyermob.mobsstorage.world.NetworkPortBlockEntity;

public final class StorageResolver {
    private StorageResolver() {
    }

    public static boolean eligible(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModTags.LABELABLE_STORAGE) && level.getBlockEntity(pos) instanceof Container;
    }

    public static boolean networkEligible(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return eligible(level, pos)
                || (level.getBlockState(pos).is(ModTags.NETWORK_STORAGE) && blockEntity != null)
                || blockEntity instanceof NetworkInterfaceBlockEntity
                || blockEntity instanceof NetworkPortBlockEntity;
    }

    public static List<BlockEntity> logicalStorage(Level level, BlockPos pos) {
        BlockEntity first = level.getBlockEntity(pos);
        if (first instanceof NetworkInterfaceBlockEntity || first instanceof NetworkPortBlockEntity) {
            return List.of(first);
        }
        if (first != null && level.getBlockState(pos).is(ModTags.NETWORK_STORAGE)
                && !(first instanceof Container)) {
            return List.of(first);
        }
        if (!(first instanceof Container) || !level.getBlockState(pos).is(ModTags.LABELABLE_STORAGE)) {
            return List.of();
        }
        BlockState state = level.getBlockState(pos);
        if (first instanceof ChestBlockEntity && state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos otherPos = pos.relative(ChestBlock.getConnectedDirection(state));
            BlockEntity other = level.getBlockEntity(otherPos);
            if (other instanceof ChestBlockEntity && level.getBlockState(otherPos).getBlock() == state.getBlock()) {
                return state.getValue(ChestBlock.TYPE) == ChestType.RIGHT
                        ? List.of(first, other)
                        : List.of(other, first);
            }
        }
        return List.of(first);
    }

    public static Optional<LabelData> findLabel(Level level, BlockPos pos) {
        return logicalStorage(level, pos).stream()
                .map(StorageResolver::existingLabel)
                .flatMap(Optional::stream)
                .findFirst();
    }

    public static Optional<LabelData> existingLabel(BlockEntity blockEntity) {
        return rawLabel(blockEntity).filter(label -> ownsAnchor(blockEntity, label.anchor()));
    }

    public static Optional<LabelData> rawLabel(BlockEntity blockEntity) {
        return blockEntity.getExistingData(ModAttachments.STORAGE_LABEL).filter(LabelData::configured);
    }

    public static boolean ownsAnchor(BlockEntity blockEntity, BlockPos anchor) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            return false;
        }
        if (blockEntity.getBlockPos().equals(anchor) || !level.isLoaded(anchor)) {
            return true;
        }
        return logicalStorage(level, blockEntity.getBlockPos()).stream()
                .anyMatch(storage -> storage.getBlockPos().equals(anchor));
    }

    public static void reconcileLabelTopology(Level level, BlockPos pos) {
        List<BlockEntity> storage = logicalStorage(level, pos);
        if (storage.isEmpty()) {
            return;
        }
        List<BlockPos> positions = storage.stream().map(BlockEntity::getBlockPos).toList();
        for (BlockEntity blockEntity : storage) {
            Optional<LabelData> label = rawLabel(blockEntity);
            if (label.isPresent() && level.isLoaded(label.get().anchor())
                    && !positions.contains(label.get().anchor())) {
                clearLabel(level, List.of(blockEntity));
            }
        }
    }

    public static Optional<LabelData> labelFor(Container container) {
        if (container instanceof BlockEntity blockEntity) {
            return existingLabel(blockEntity);
        }
        if (container instanceof CompoundContainer compound) {
            CompoundContainerAccessor accessor = (CompoundContainerAccessor) compound;
            Optional<LabelData> first = labelFor(accessor.mobsstorage$getFirst());
            return first.isPresent() ? first : labelFor(accessor.mobsstorage$getSecond());
        }
        return Optional.empty();
    }

    public static boolean allows(Container container, ItemStack stack) {
        if (container instanceof BlockEntity blockEntity) {
            return existingLabel(blockEntity).map(label -> label.allows(stack, blockEntity.getLevel())).orElse(true);
        }
        if (container instanceof CompoundContainer compound) {
            CompoundContainerAccessor accessor = (CompoundContainerAccessor) compound;
            return allows(accessor.mobsstorage$getFirst(), stack) && allows(accessor.mobsstorage$getSecond(), stack);
        }
        return true;
    }

    public static void setLabel(Level level, List<BlockEntity> storage, LabelData data) {
        for (BlockEntity blockEntity : storage) {
            blockEntity.setData(ModAttachments.STORAGE_LABEL, data);
            blockEntity.setChanged();
            blockEntity.syncData(ModAttachments.STORAGE_LABEL);
            level.invalidateCapabilities(blockEntity.getBlockPos());
        }
    }

    public static void clearLabel(Level level, List<BlockEntity> storage) {
        for (BlockEntity blockEntity : storage) {
            blockEntity.removeData(ModAttachments.STORAGE_LABEL);
            blockEntity.setChanged();
            blockEntity.syncData(ModAttachments.STORAGE_LABEL);
            level.invalidateCapabilities(blockEntity.getBlockPos());
        }
    }
}
