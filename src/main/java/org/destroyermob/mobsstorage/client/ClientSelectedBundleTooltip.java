package org.destroyermob.mobsstorage.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.apache.commons.lang3.math.Fraction;
import org.destroyermob.mobsstorage.inventory.SelectedBundleTooltip;

final class ClientSelectedBundleTooltip implements ClientTooltipComponent {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.withDefaultNamespace("container/bundle/background");
    private static final ResourceLocation SLOT =
            ResourceLocation.withDefaultNamespace("container/bundle/slot");
    private static final ResourceLocation BLOCKED_SLOT =
            ResourceLocation.withDefaultNamespace("container/bundle/blocked_slot");
    private final BundleContents contents;
    private final int selectedItem;

    ClientSelectedBundleTooltip(SelectedBundleTooltip tooltip) {
        contents = tooltip.contents();
        selectedItem = tooltip.selectedItem();
    }

    @Override
    public int getHeight() {
        return backgroundHeight() + (contents.isEmpty() ? 4 : 14);
    }

    @Override
    public int getWidth(Font font) {
        return contents.isEmpty() ? backgroundWidth()
                : Math.max(backgroundWidth(), font.width(hint()));
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        int columns = gridSizeX();
        int rows = gridSizeY();
        graphics.blitSprite(BACKGROUND, x, y, backgroundWidth(), backgroundHeight());
        boolean full = contents.weight().compareTo(Fraction.ONE) >= 0;
        int itemIndex = 0;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                renderSlot(x + column * 18 + 1, y + row * 20 + 1,
                        itemIndex++, full, graphics, font);
            }
        }
        if (!contents.isEmpty()) {
            graphics.drawString(font, hint(), x, y + backgroundHeight() + 3, 0xFFAAAAAA, false);
        }
    }

    private void renderSlot(int x, int y, int itemIndex, boolean full,
                            GuiGraphics graphics, Font font) {
        if (itemIndex >= contents.size()) {
            graphics.blitSprite(full ? BLOCKED_SLOT : SLOT, x, y, 0, 18, 20);
            return;
        }
        ItemStack stack = contents.getItemUnsafe(itemIndex);
        graphics.blitSprite(SLOT, x, y, 0, 18, 20);
        graphics.renderItem(stack, x + 1, y + 1, itemIndex);
        graphics.renderItemDecorations(font, stack, x + 1, y + 1);
        if (itemIndex == selectedItem) {
            AbstractContainerScreen.renderSlotHighlight(graphics, x + 1, y + 1, 0);
        }
    }

    private int backgroundWidth() {
        return gridSizeX() * 18 + 2;
    }

    private int backgroundHeight() {
        return gridSizeY() * 20 + 2;
    }

    private int gridSizeX() {
        return Math.max(2, (int) Math.ceil(Math.sqrt(contents.size() + 1.0D)));
    }

    private int gridSizeY() {
        return (int) Math.ceil((contents.size() + 1.0D) / gridSizeX());
    }

    private Component hint() {
        return Component.translatable(contents.size() > 1
                ? "tooltip.mobsstorage.bundle_scroll"
                : "tooltip.mobsstorage.bundle_take");
    }
}
