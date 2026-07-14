package org.destroyermob.mobsstorage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.destroyermob.mobsstorage.storage.LabelData;
import org.destroyermob.mobsstorage.storage.LabelDisplayMode;
import org.destroyermob.mobsstorage.storage.StorageResolver;

public final class StorageLabelRenderer {
    private static final Map<BlockPos, LabelData> CACHE = new LinkedHashMap<>();
    private static final Map<BlockPos, AnimationState> ANIMATIONS = new LinkedHashMap<>();
    private static final float ENTER_RESPONSE = 13.0F;
    private static final float EXIT_RESPONSE = 9.0F;
    private static final float MIN_VISIBLE = 0.002F;
    private static int refreshTicks;
    private static long lastWorldFrameNanos;
    private static long lastHudFrameNanos;
    private static float hudVisibility;
    private static ResourceLocation hudIcon = LabelData.AIR;

    private StorageLabelRenderer() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            CACHE.clear();
            ANIMATIONS.clear();
            hudVisibility = 0.0F;
            hudIcon = LabelData.AIR;
            lastWorldFrameNanos = 0L;
            lastHudFrameNanos = 0L;
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
        Map<BlockPos, RenderTarget> desired = new LinkedHashMap<>();
        CACHE.forEach((pos, label) -> {
            if (label.alwaysShow() && label.displayMode() != LabelDisplayMode.CROSSHAIR) {
                desired.put(pos, new RenderTarget(pos, label.face(), label));
            }
        });
        if (minecraft.hitResult instanceof BlockHitResult hit) {
            StorageResolver.findLabel(minecraft.level, hit.getBlockPos())
                    .filter(label -> label.displayMode() != LabelDisplayMode.CROSSHAIR)
                    .ifPresent(label -> desired.put(label.anchor(),
                            new RenderTarget(label.anchor(), label.face(), label)));
        }

        float deltaSeconds = worldDeltaSeconds();
        Set<BlockPos> positions = new HashSet<>(ANIMATIONS.keySet());
        positions.addAll(desired.keySet());
        for (BlockPos pos : positions) {
            RenderTarget target = desired.get(pos);
            AnimationState state = ANIMATIONS.computeIfAbsent(pos, unused -> new AnimationState());
            state.advance(target, deltaSeconds);
        }
        ANIMATIONS.entrySet().removeIf(entry -> !desired.containsKey(entry.getKey())
                && entry.getValue().visibility <= MIN_VISIBLE);
        if (ANIMATIONS.isEmpty()) {
            return;
        }

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        for (AnimationState state : ANIMATIONS.values()) {
            if (state.visibility > MIN_VISIBLE && minecraft.level.isLoaded(state.pos)) {
                renderLabel(minecraft, event, buffers, state);
            }
        }
        buffers.endBatch();
    }

    public static void renderHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            hudVisibility = 0.0F;
            return;
        }
        ResourceLocation targetIcon = null;
        if (minecraft.screen == null && !minecraft.options.hideGui
                && minecraft.hitResult instanceof BlockHitResult hit) {
            targetIcon = StorageResolver.findLabel(minecraft.level, hit.getBlockPos())
                    .filter(label -> label.displayMode() == LabelDisplayMode.CROSSHAIR)
                    .map(LabelData::icon)
                    .filter(icon -> BuiltInRegistries.ITEM.get(icon) != net.minecraft.world.item.Items.AIR)
                    .orElse(null);
        }
        if (targetIcon != null) {
            hudIcon = targetIcon;
        }
        float targetVisibility = targetIcon == null ? 0.0F : 1.0F;
        hudVisibility = damp(hudVisibility, targetVisibility,
                targetVisibility > hudVisibility ? ENTER_RESPONSE : EXIT_RESPONSE, hudDeltaSeconds());
        if (hudVisibility <= MIN_VISIBLE || minecraft.screen != null || minecraft.options.hideGui) {
            return;
        }
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(hudIcon);
        if (item == net.minecraft.world.item.Items.AIR) {
            return;
        }

        float reveal = smoothstep(hudVisibility);
        int x = event.getGuiGraphics().guiWidth() / 2 + 12 + Math.round((1.0F - reveal) * 7.0F);
        int y = event.getGuiGraphics().guiHeight() / 2 - 10;
        int panelAlpha = Math.round(174.0F * reveal);
        int outlineAlpha = Math.round(208.0F * reveal);
        event.getGuiGraphics().fill(x - 1, y - 1, x + 20, y + 20, panelAlpha << 24);
        event.getGuiGraphics().fill(x - 2, y - 2, x + 18, y + 18,
                panelAlpha << 24 | 0x101010);
        event.getGuiGraphics().renderOutline(x - 2, y - 2, 20, 20,
                outlineAlpha << 24 | 0xFFFFFF);

        PoseStack poses = event.getGuiGraphics().pose();
        float iconScale = 0.68F + reveal * 0.32F;
        poses.pushPose();
        poses.translate(x + 8.0F, y + 8.0F, 0.0F);
        poses.scale(iconScale, iconScale, 1.0F);
        poses.translate(-(x + 8.0F), -(y + 8.0F), 0.0F);
        event.getGuiGraphics().renderFakeItem(new ItemStack(item), x, y);
        poses.popPose();
    }

    private static void renderLabel(
            Minecraft minecraft,
            RenderLevelStageEvent event,
            MultiBufferSource buffers,
            AnimationState state
    ) {
        BlockPos pos = state.pos;
        Direction face = state.face;
        LabelData label = state.label;
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(label.icon());
        if (item == net.minecraft.world.item.Items.AIR) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 relative = Vec3.atCenterOf(pos)
                .add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.57D))
                .subtract(camera.getPosition());
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double seconds = (minecraft.level.getGameTime() + partial) / 20.0D;
        double phase = (pos.hashCode() & 1023) * (Math.PI * 2.0D / 1024.0D);
        boolean billboard = label.displayMode() == LabelDisplayMode.BILLBOARD;
        double bob = Math.sin(seconds * (Math.PI * 2.0D / 2.8D) + phase)
                * (billboard ? 0.026D : 0.008D);
        float reveal = smoothstep(state.visibility);
        float breathe = billboard
                ? 1.0F + (float) Math.sin(seconds * (Math.PI * 2.0D / 3.6D) + phase) * 0.012F
                : 1.0F;
        float revealScale = reveal * (0.68F + reveal * 0.32F);
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(relative.x, relative.y + bob, relative.z);
        ItemDisplayContext context;
        if (label.displayMode() == LabelDisplayMode.SURFACE) {
            switch (face) {
                case UP -> poses.mulPose(Axis.XP.rotationDegrees(90.0F));
                case DOWN -> poses.mulPose(Axis.XP.rotationDegrees(-90.0F));
                default -> poses.mulPose(Axis.YP.rotationDegrees(180.0F - face.toYRot()));
            }
            context = ItemDisplayContext.FIXED;
        } else {
            poses.mulPose(camera.rotation());
            poses.mulPose(Axis.YP.rotationDegrees(180.0F));
            poses.mulPose(Axis.ZP.rotationDegrees(
                    (float) Math.sin(seconds * (Math.PI * 2.0D / 4.5D) + phase) * 1.35F));
            context = ItemDisplayContext.GUI;
        }
        float scale = 0.55F * revealScale * breathe;
        poses.scale(scale, scale, scale);
        minecraft.getItemRenderer().renderStatic(
                new ItemStack(item),
                context,
                LevelRenderer.getLightColor(minecraft.level, pos),
                OverlayTexture.NO_OVERLAY,
                poses,
                buffers,
                minecraft.level,
                pos.hashCode()
        );
        poses.popPose();
    }

    private record RenderTarget(BlockPos pos, Direction face, LabelData label) {
    }

    private static float damp(float current, float target, float response, float deltaSeconds) {
        float blend = 1.0F - (float) Math.exp(-response * deltaSeconds);
        return Mth.lerp(blend, current, target);
    }

    private static float smoothstep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float worldDeltaSeconds() {
        long now = System.nanoTime();
        float delta = lastWorldFrameNanos == 0L ? 1.0F / 60.0F
                : (now - lastWorldFrameNanos) / 1_000_000_000.0F;
        lastWorldFrameNanos = now;
        return Mth.clamp(delta, 0.0F, 0.05F);
    }

    private static float hudDeltaSeconds() {
        long now = System.nanoTime();
        float delta = lastHudFrameNanos == 0L ? 1.0F / 60.0F
                : (now - lastHudFrameNanos) / 1_000_000_000.0F;
        lastHudFrameNanos = now;
        return Mth.clamp(delta, 0.0F, 0.05F);
    }

    private static final class AnimationState {
        private BlockPos pos = BlockPos.ZERO;
        private Direction face = Direction.NORTH;
        private LabelData label = LabelData.EMPTY;
        private float visibility;

        private void advance(RenderTarget target, float deltaSeconds) {
            if (target != null) {
                pos = target.pos();
                face = target.face();
                label = target.label();
            }
            float desired = target == null ? 0.0F : 1.0F;
            visibility = damp(visibility, desired,
                    desired > visibility ? ENTER_RESPONSE : EXIT_RESPONSE, deltaSeconds);
        }
    }
}
