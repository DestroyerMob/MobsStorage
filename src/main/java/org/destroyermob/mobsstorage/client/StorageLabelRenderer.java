package org.destroyermob.mobsstorage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.destroyermob.mobsstorage.storage.LabelData;
import org.destroyermob.mobsstorage.storage.StorageResolver;

public final class StorageLabelRenderer {
    private static final Map<BlockPos, LabelData> CACHE = new LinkedHashMap<>();
    private static int refreshTicks;

    private StorageLabelRenderer() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            CACHE.clear();
            return;
        }
        if (++refreshTicks < 20) {
            return;
        }
        refreshTicks = 0;
        refresh(minecraft.level, minecraft.player.chunkPosition(), minecraft.options.getEffectiveRenderDistance());
    }

    private static void refresh(ClientLevel level, ChunkPos center, int radius) {
        CACHE.clear();
        for (int chunkX = center.x - radius; chunkX <= center.x + radius; chunkX++) {
            for (int chunkZ = center.z - radius; chunkZ <= center.z + radius; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    StorageResolver.existingLabel(blockEntity)
                            .filter(label -> label.anchor().equals(blockEntity.getBlockPos()))
                            .ifPresent(label -> CACHE.put(blockEntity.getBlockPos().immutable(), label));
                }
            }
        }
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Map<BlockPos, LabelData> visible = new LinkedHashMap<>();
        CACHE.forEach((pos, label) -> {
            if (label.alwaysShow()) {
                visible.put(pos, label);
            }
        });
        if (minecraft.hitResult instanceof BlockHitResult hit) {
            StorageResolver.findLabel(minecraft.level, hit.getBlockPos()).ifPresent(label -> visible.put(label.anchor(), label));
        }
        if (visible.isEmpty()) {
            return;
        }

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        for (Map.Entry<BlockPos, LabelData> entry : visible.entrySet()) {
            if (minecraft.level.isLoaded(entry.getKey())) {
                renderLabel(minecraft, event, buffers, entry.getKey(), entry.getValue());
            }
        }
        buffers.endBatch();
    }

    private static void renderLabel(
            Minecraft minecraft,
            RenderLevelStageEvent event,
            MultiBufferSource buffers,
            BlockPos pos,
            LabelData label
    ) {
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(label.icon());
        if (item == net.minecraft.world.item.Items.AIR) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 relative = Vec3.atCenterOf(pos)
                .add(Vec3.atLowerCornerOf(label.face().getNormal()).scale(0.57D))
                .subtract(camera.getPosition());
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double bob = Math.sin((minecraft.level.getGameTime() + partial) * 0.12D) * 0.035D;
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(relative.x, relative.y + bob, relative.z);
        switch (label.face()) {
            case UP -> poses.mulPose(Axis.XP.rotationDegrees(90.0F));
            case DOWN -> poses.mulPose(Axis.XP.rotationDegrees(-90.0F));
            default -> poses.mulPose(Axis.YP.rotationDegrees(180.0F - label.face().toYRot()));
        }
        poses.scale(0.55F, 0.55F, 0.55F);
        minecraft.getItemRenderer().renderStatic(
                new ItemStack(item),
                ItemDisplayContext.FIXED,
                LevelRenderer.getLightColor(minecraft.level, pos),
                OverlayTexture.NO_OVERLAY,
                poses,
                buffers,
                minecraft.level,
                pos.hashCode()
        );
        poses.popPose();
    }
}
