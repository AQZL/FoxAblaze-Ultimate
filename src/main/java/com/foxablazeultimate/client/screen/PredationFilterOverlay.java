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

/**
 * 拉斐尔捕食过滤器叠加层：贴在暴食者 / 暴食之王虚数仓库 GUI 顶部的纯客户端组件。
 *
 * <p>不开独立 GUI 也不修改原容器，所有过滤槽是"伪槽位"——客户端缓存 {@code List<ItemStack>}，
 * 渲染在 GUI 顶部上方；点击时向服务端发 {@link UpdatePredationFilterPayload}，由服务端写 NBT 并
 * 通过 {@link com.foxablazeultimate.network.SyncPredationFilterPayload} 回推全量同步。
 *
 * <h3>位置约定</h3>
 * <ul>
 *   <li>按钮：固定在 {@code (leftPos + 20, topPos + buttonYInset)}，{@code buttonYInset} 由每个屏幕
 *       在 {@link #onScreenInit(int)} 时传入（暴食之王 = 2；暴食者 = -21，按钮底边贴 GUI 顶边）；</li>
 *   <li>展开行：在按钮<b>右侧</b>，单行水平排列；面板左边贴在按钮右边（无间隙）、垂直居中：
 *       {@code rowStartX = buttonX + BUTTON_WIDTH + ROW_FRAME_INSET}、
 *       {@code rowY = buttonY + (BUTTON_HEIGHT - SLOT_SIZE) / 2}。</li>
 * </ul>
 *
 * <h3>过滤槽放置规则</h3>
 * <ul>
 *   <li>只允许放入 {@link BlockItem}（方块对应的物品）；非方块物品被拒收。</li>
 *   <li>同一种 {@link net.minecraft.world.item.Item} 只能在过滤器里出现一次；尝试放重复物会被拒收。</li>
 *   <li>清空槽位（空手点击）永远允许。</li>
 * </ul>
 * <p>双端校验：客户端先在 {@link #mouseClicked} 拦下非法放置（无网络流量），服务端在
 * {@link com.foxablazeultimate.predation.PredationFilterHelper#setSlot} 再校验一遍并通过
 * {@code sendSync} 回滚客户端可能的乐观更新。
 *
 * <p>调用方（{@link com.foxablazeultimate.client.screen.BeelzebubStorageScreen} 与
 * {@code MixinSpatialStorageScreen}）在 {@code init / renderBg / renderTooltip / mouseClicked}
 * 四个时机依次转发 {@link #onScreenInit}, {@link #render}, {@link #renderTooltip}, {@link #mouseClicked}。
 */
public final class PredationFilterOverlay {
    /** 按钮贴图：24×42（两个 21 高的状态，上 normal、下 hover）。 */
    public static final ResourceLocation BUTTON_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            FoxAblazeUltimateMod.MOD_ID, "textures/gui/predation_filter.png");
    public static final int BUTTON_WIDTH = 24;
    public static final int BUTTON_HEIGHT = 21;
    /** 按钮在 GUI 内的固定 X 偏移（两个 GUI 都用 x=20）。 */
    private static final int BUTTON_X_INSET = 20;
    /** 按钮 Y 偏移的默认值（暴食之王 GUI 用），可被 {@link #onScreenInit(int)} 覆盖。 */
    private static final int DEFAULT_BUTTON_Y_INSET = 2;
    private static final int SLOT_SIZE = 18;
    /** 行面板四周框边宽度（同时决定面板与按钮顶部的间距，保证面板不覆盖按钮）。 */
    private static final int ROW_FRAME_INSET = 4;

    // 面板主题色（近似原版深蓝 GUI）
    private static final int COLOR_FRAME_OUTER = 0xFF0F1424; // 深色外框
    private static final int COLOR_FRAME_INNER = 0xFF2C3650; // 中间面
    private static final int COLOR_HIGHLIGHT   = 0xFF5A6A8C; // 亮色高光
    private static final int COLOR_SLOT_BG     = 0xFF1A2236; // 槽位底色
    private static final int COLOR_SLOT_DARK   = 0xFF373737; // 槽位入陷边深色

    private static int slotCount;
    private static List<ItemStack> items = new ArrayList<>();
    private static boolean expanded;
    /** 当前激活 GUI 选择的按钮 Y 偏移；每次 {@link #onScreenInit(int)} 时由屏幕传入。 */
    private static int buttonYInset = DEFAULT_BUTTON_Y_INSET;

    private PredationFilterOverlay() {}

    // ============================================================
    // |                     Sync hooks                           |
    // ============================================================

    /**
     * 客户端 screen.init 时调用：清空展开态、缓存本次 GUI 的按钮 Y 偏移、并主动向服务端拉一次全量同步。
     *
     * @param buttonYInsetForScreen 当前 GUI 内按钮的 Y 偏移（相对 {@code topPos}）。
     *                              暴食之王 ({@code BeelzebubStorageScreen}) 传 {@code 2}（落在 title 上方空白）；
     *                              暴食者 ({@code SpatialStorageScreen}) 传 {@code -17}（底部 4px 嵌入 GUI 顶边）。
     */
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

    /**
     * 把当前 overlay 占用的矩形列表通过 {@code consumer} 推出去。
     *
     * <p>主要给第三方 UI mod（EMI / JEI / REI 等）注册排除区域用，让它们的侧栏避开 overlay。
     * 永远会推按钮矩形；展开时还会推面板矩形（含 4px 边框）。
     *
     * <p>无 Raphael 时不推任何矩形（overlay 不会渲染）。
     */
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

    /** {@link #forEachOverlayBound} 的回调；用 {@code (x, y, w, h)} 描述一个矩形。 */
    @FunctionalInterface
    public interface BoundsConsumer {
        void accept(int x, int y, int width, int height);
    }

    /** 给 hasClickedOutside 用：鼠标是否在按钮 或 （展开时）行面板区域。 */
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

    /** {@link com.foxablazeultimate.network.SyncPredationFilterPayload} 的客户端处理入口。 */
    public static void applySync(int incomingSlots, List<ItemStack> incomingItems) {
        slotCount = Math.max(0, incomingSlots);
        items = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            ItemStack stack = i < incomingItems.size() ? incomingItems.get(i) : ItemStack.EMPTY;
            items.add(stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }
    }

    // ============================================================
    // |                     Rendering                            |
    // ============================================================

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
        if (ghost.isEmpty()) return; // 空槽位不显示 tooltip
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
        // 面板：外深色 → 中面 → 顶部高光一条
        graphics.fill(panelX, panelY, panelX + totalW, panelY + totalH, COLOR_FRAME_OUTER);
        graphics.fill(panelX + 1, panelY + 1, panelX + totalW - 1, panelY + totalH - 1, COLOR_FRAME_INNER);
        graphics.fill(panelX + 1, panelY + 1, panelX + totalW - 1, panelY + 2, COLOR_HIGHLIGHT);
        // 每个槽位：18×18，一圈深色入陷 + 底色
        for (int i = 0; i < slotCount; i++) {
            int sx = rowX + i * SLOT_SIZE;
            int sy = rowYPos;
            graphics.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, COLOR_SLOT_DARK);
            graphics.fill(sx + 1, sy + 1, sx + SLOT_SIZE, sy + SLOT_SIZE, COLOR_HIGHLIGHT);
            graphics.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, COLOR_SLOT_BG);
        }
    }

    // ============================================================
    // |                     Mouse handling                       |
    // ============================================================

    public static boolean mouseClicked(double mouseX, double mouseY, int button,
                                       int leftPos, int topPos, int imageWidth) {
        if (!hasRaphael()) return false;
        int btnX = buttonX(leftPos);
        int btnY = buttonY(topPos);

        // 1) 点按钮：切换展开。
        if (isMouseOver(btnX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)) {
            if (button == 0) {
                expanded = !expanded;
                playClickSound(1.0F);
            }
            return true;
        }

        // 2) 点在行面板区域内（含 4px 边框）一律吃掉，避免原版误认为 GUI 外部点击。
        if (!isMouseOverOverlay(mouseX, mouseY, leftPos, topPos, imageWidth)) return false;

        // 3) 展开时如果点在某个过滤槽上，提交鬼影。未展开或未命中槽位但在范围内仕然吃掉点击。
        if (expanded) {
            int hovered = hoveredSlot(leftPos, topPos, mouseX, mouseY);
            if (hovered >= 0) {
                ItemStack carried = currentCarried();
                ItemStack ghost = carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1);
                // 校验：放入非空时必须是 BlockItem 且不能与其它槽位重复（清空永远允许）。
                if (!ghost.isEmpty() && !canAccept(ghost, hovered)) {
                    playClickSound(0.25F); // 轻微反馈，表示点击被吃掉但拒收
                    return true;
                }
                // 客户端立即反应一份（让 UI 立即看到鬼影），服务端 Sync 回推后再覆盖。
                if (hovered < items.size()) items.set(hovered, ghost);
                NetworkManager.sendToServer(new UpdatePredationFilterPayload(hovered, ghost));
                playClickSound(0.6F);
            }
        }
        return true;
    }

    /**
     * 客户端预校验：当前 {@code ghost} 是否可放入 {@code targetSlot}。
     * <ul>
     *   <li>必须是 {@link BlockItem}；</li>
     *   <li>除 {@code targetSlot} 之外的任何已用槽位不能已经有同一种物品。</li>
     * </ul>
     */
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

    // ============================================================
    // |                     Geometry helpers                     |
    // ============================================================

    private static int buttonX(int leftPos) {
        return leftPos + BUTTON_X_INSET;
    }

    private static int buttonY(int topPos) {
        return topPos + buttonYInset;
    }

    private static int rowStartX(int leftPos) {
        // 面板左边（rowStartX - ROW_FRAME_INSET）正好贴在按钮右边。
        return buttonX(leftPos) + BUTTON_WIDTH + ROW_FRAME_INSET;
    }

    private static int rowY(int topPos) {
        // 18 高的槽位在 21 高的按钮里垂直居中（+1 视觉对齐）。
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
