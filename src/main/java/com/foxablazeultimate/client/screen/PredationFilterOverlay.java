package com.foxablazeultimate.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.network.RequestPredationFilterSyncPayload;
import com.foxablazeultimate.network.UpdatePredationFilterPayload;
import com.foxablazeultimate.predation.PredationFilterHelper;

import dev.architectury.networking.NetworkManager;
import io.github.manasmods.tensura.util.client.RenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public final class PredationFilterOverlay {

    public static final ResourceLocation BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FoxAblazeUltimateMod.MOD_ID, "textures/gui/predation_filter.png");
    public static final int BUTTON_WIDTH = 24;
    public static final int BUTTON_HEIGHT = 21;

    private static final int BUTTON_X_INSET = 20;

    private static final int DEFAULT_BUTTON_Y_INSET = 2;
    private static final int SLOT_SIZE = 18;

    private static final int ROW_FRAME_INSET = 4;

    private static final int COLOR_FRAME_OUTER = 0xFF0F1424;
    private static final int COLOR_FRAME_INNER = 0xFF2C3650;
    private static final int COLOR_HIGHLIGHT   = 0xFF5A6A8C;
    private static final int COLOR_SLOT_BG     = 0xFF1A2236;
    private static final int COLOR_SLOT_DARK   = 0xFF373737;

    private static int slotCount;
    private static List<ItemStack> items = new ArrayList<>();
    private static boolean expanded;

    private static int buttonYInset = DEFAULT_BUTTON_Y_INSET;

    private PredationFilterOverlay() {}

    public static void onScreenInit(int buttonYInsetForScreen) {
        buttonYInset = buttonYInsetForScreen;
        expanded = false;
        if (!hasRaphael()) {
            slotCount = 0;
            items = new ArrayList<>();
            return;
        }
        NetworkManager.sendToServer(new RequestPredationFilterSyncPayload());
    }

    public static void forEachOverlayBound(int leftPos, int topPos, int imageWidth, BoundsConsumer consumer) {
        if (!hasRaphael()) return;
        consumer.accept(buttonX(leftPos), buttonY(topPos), BUTTON_WIDTH, BUTTON_HEIGHT);
        if (expanded && slotCount > 0) {
            int rowX = rowStartX(leftPos);
            int rowYpos = rowY(topPos);
            consumer.accept(
                    rowX - ROW_FRAME_INSET,
                    rowYpos - ROW_FRAME_INSET,
                    slotCount * SLOT_SIZE + ROW_FRAME_INSET * 2,
                    SLOT_SIZE + ROW_FRAME_INSET * 2);
        }
    }

    @FunctionalInterface
    public interface BoundsConsumer {
        void accept(int x, int y, int width, int height);
    }

    public static boolean isMouseOverOverlay(double mouseX, double mouseY,
                                             int leftPos, int topPos, int imageWidth) {
        if (!hasRaphael()) return false;
        int btnX = buttonX(leftPos);
        int btnY = buttonY(topPos);
        if (isMouseOver(btnX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)) return true;
        if (!expanded) return false;
        int rowX = rowStartX(leftPos);
        int rowYPos = rowY(topPos);
        int x1 = rowX - ROW_FRAME_INSET;
        int x2 = rowX + slotCount * SLOT_SIZE + ROW_FRAME_INSET;
        int y1 = rowYPos - ROW_FRAME_INSET;
        int y2 = rowYPos + SLOT_SIZE + ROW_FRAME_INSET;
        return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
    }

    public static void applySync(int incomingSlots, List<ItemStack> incomingItems) {
        slotCount = Math.max(0, incomingSlots);
        items = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            ItemStack stack = i < incomingItems.size() ? incomingItems.get(i) : ItemStack.EMPTY;
            items.add(stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }
    }

    public static void render(GuiGraphics graphics, int leftPos, int topPos, int imageWidth,
                              int mouseX, int mouseY) {
        if (!hasRaphael()) return;
        if (expanded) {
            renderRow(graphics, leftPos, topPos);
            renderGhostItems(graphics, leftPos, topPos);
        }
        renderButton(graphics, leftPos, topPos, mouseX, mouseY);
    }

    private static void renderGhostItems(GuiGraphics graphics, int leftPos, int topPos) {
        int rowX = rowStartX(leftPos);
        int rowYPos = rowY(topPos);
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < slotCount; i++) {
            ItemStack ghost = ghostAt(i);
            if (ghost.isEmpty()) continue;
            int x = rowX + i * SLOT_SIZE + 1;
            int y = rowYPos + 1;
            graphics.renderItem(ghost, x, y);
            graphics.renderItemDecorations(mc.font, ghost, x, y, "");
        }
    }

    public static void renderTooltip(GuiGraphics graphics, int leftPos, int topPos, int imageWidth,
                                     int mouseX, int mouseY) {
        if (!hasRaphael()) return;
        int buttonX = buttonX(leftPos);
        int buttonY = buttonY(topPos);
        if (isMouseOver(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)) {
            Component header = Component.translatable("foxablazeultimate.predation_filter.button");
            Component hint = Component.translatable(expanded
                    ? "foxablazeultimate.predation_filter.button.collapse"
                    : "foxablazeultimate.predation_filter.button.expand");
            graphics.renderTooltip(Minecraft.getInstance().font,
                    List.of(header.getVisualOrderText(), hint.getVisualOrderText()), mouseX, mouseY);
            return;
        }
        if (!expanded) return;
        int hovered = hoveredSlot(leftPos, topPos, mouseX, mouseY);
        if (hovered < 0) return;
        ItemStack ghost = ghostAt(hovered);
        if (ghost.isEmpty()) return;
        graphics.renderTooltip(Minecraft.getInstance().font, ghost, mouseX, mouseY);
    }

    private static void renderButton(GuiGraphics graphics, int leftPos, int topPos, int mouseX, int mouseY) {
        int x = buttonX(leftPos);
        int y = buttonY(topPos);
        boolean hover = isMouseOver(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY);
        int v = hover || expanded ? BUTTON_HEIGHT : 0;
        graphics.blit(BUTTON_TEXTURE, x, y, 0.0F, (float) v, BUTTON_WIDTH, BUTTON_HEIGHT,
                BUTTON_WIDTH, BUTTON_HEIGHT * 2);
    }

    private static void renderRow(GuiGraphics graphics, int leftPos, int topPos) {
        int rowX = rowStartX(leftPos);
        int rowYPos = rowY(topPos);
        int totalW = slotCount * SLOT_SIZE + ROW_FRAME_INSET * 2;
        int totalH = SLOT_SIZE + ROW_FRAME_INSET * 2;
        int panelX = rowX - ROW_FRAME_INSET;
        int panelY = rowYPos - ROW_FRAME_INSET;

        graphics.fill(panelX, panelY, panelX + totalW, panelY + totalH, COLOR_FRAME_OUTER);
        graphics.fill(panelX + 1, panelY + 1, panelX + totalW - 1, panelY + totalH - 1, COLOR_FRAME_INNER);
        graphics.fill(panelX + 1, panelY + 1, panelX + totalW - 1, panelY + 2, COLOR_HIGHLIGHT);

        for (int i = 0; i < slotCount; i++) {
            int sx = rowX + i * SLOT_SIZE;
            int sy = rowYPos;
            graphics.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, COLOR_SLOT_DARK);
            graphics.fill(sx + 1, sy + 1, sx + SLOT_SIZE, sy + SLOT_SIZE, COLOR_HIGHLIGHT);
            graphics.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, COLOR_SLOT_BG);
        }
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button,
                                       int leftPos, int topPos, int imageWidth) {
        if (!hasRaphael()) return false;
        int btnX = buttonX(leftPos);
        int btnY = buttonY(topPos);

        if (isMouseOver(btnX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)) {
            if (button == 0) {
                expanded = !expanded;
                playClickSound(1.0F);
            }
            return true;
        }

        if (!isMouseOverOverlay(mouseX, mouseY, leftPos, topPos, imageWidth)) return false;

        if (expanded) {
            int hovered = hoveredSlot(leftPos, topPos, mouseX, mouseY);
            if (hovered >= 0) {
                ItemStack carried = currentCarried();
                ItemStack ghost = carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1);

                if (!ghost.isEmpty() && !canAccept(ghost, hovered)) {
                    playClickSound(0.25F);
                    return true;
                }

                if (hovered < items.size()) items.set(hovered, ghost);
                NetworkManager.sendToServer(new UpdatePredationFilterPayload(hovered, ghost));
                playClickSound(0.6F);
            }
        }
        return true;
    }

    private static boolean canAccept(ItemStack ghost, int targetSlot) {
        if (!(ghost.getItem() instanceof BlockItem)) return false;
        for (int i = 0; i < items.size(); i++) {
            if (i == targetSlot) continue;
            ItemStack entry = items.get(i);
            if (!entry.isEmpty() && entry.is(ghost.getItem())) return false;
        }
        return true;
    }

    private static void playClickSound(float volume) {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, volume));
    }

    private static int buttonX(int leftPos) {
        return leftPos + BUTTON_X_INSET;
    }

    private static int buttonY(int topPos) {
        return topPos + buttonYInset;
    }

    private static int rowStartX(int leftPos) {

        return buttonX(leftPos) + BUTTON_WIDTH + ROW_FRAME_INSET;
    }

    private static int rowY(int topPos) {

        return buttonY(topPos) + (BUTTON_HEIGHT - SLOT_SIZE) / 2;
    }

    private static int hoveredSlot(int leftPos, int topPos,
                                   double mouseX, double mouseY) {
        if (slotCount <= 0) return -1;
        int rowX = rowStartX(leftPos);
        int rowYPos = rowY(topPos);
        if (mouseY < rowYPos || mouseY >= rowYPos + SLOT_SIZE) return -1;
        if (mouseX < rowX || mouseX >= rowX + slotCount * SLOT_SIZE) return -1;
        int col = (int) ((mouseX - rowX) / SLOT_SIZE);
        if (col < 0 || col >= slotCount) return -1;
        return col;
    }

    private static boolean isMouseOver(int x, int y, int w, int h, double mouseX, double mouseY) {
        return RenderHelper.mouseOver(mouseX, mouseY, x, x + w, y, y + h);
    }

    private static ItemStack ghostAt(int i) {
        if (i < 0 || i >= items.size()) return ItemStack.EMPTY;
        ItemStack stack = items.get(i);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    private static ItemStack currentCarried() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.containerMenu == null) return ItemStack.EMPTY;
        return player.containerMenu.getCarried();
    }

    private static boolean hasRaphael() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && PredationFilterHelper.hasRaphael(player);
    }
}
