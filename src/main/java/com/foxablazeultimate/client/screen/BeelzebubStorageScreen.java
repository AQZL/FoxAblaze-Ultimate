package com.foxablazeultimate.client.screen;

import java.awt.Color;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.menu.BeelzebubStorageMenu;
import com.foxablazeultimate.network.RequestBeelzebubFluidExtractPayload;

import dev.architectury.networking.NetworkManager;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.util.client.RenderHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * 暴食之王 · 虚数空间 GUI。
 *
 * <h3>布局与 {@code ResearcherStorageScreen} 完全一致</h3>
 * <ul>
 *   <li>{@code imageWidth = 256, imageHeight = 201}</li>
 *   <li>背景贴图：{@code foxablazeultimate:textures/gui/beelzebub/spatial.png}（用户提供的 bzzw.png，源 256×256，
 *       但实际显示区域只取 256×201，与 researcher_spatial.png 完全同款布局）</li>
 *   <li>翻页箭头：上一页 {@code (7, 99)}，下一页 {@code (151, 99)}</li>
 *   <li>空 slot 黑底覆盖：源贴图取 {@code (0, 201)} 处 18×18</li>
 * </ul>
 *
 * <h3>与 Researcher 的差异（已剔除）</h3>
 * <ul>
 *   <li>不渲染 ENDER_CHEST / ENCHANTING_TABLE 两个左上角 tab 图标</li>
 *   <li>不响应 {@code i == -1} 切换附魔界面</li>
 *   <li>不显示 storage_tab / enchantment_tab tooltip</li>
 * </ul>
 *
 * <h3>v2 新增：右侧中下区域水/岩浆条</h3>
 * <p>玩家反馈"虚数仓库看不到 water/lava bar"。原 Tensura {@code SpatialStorageScreen} 里两条是
 * 122 px 高、贴贴左右边框。我们的 GUI 多了合成/熔炉，left/right 没位置了，改放到熔炉下方 ~58 px 高区域（用 11 参
 * blit 把 122 贴图缩到 58 GUI px，纵向均匀压缩，gradient 视觉上同原版一致）。
 */
public class BeelzebubStorageScreen extends AbstractContainerScreen<BeelzebubStorageMenu> {

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            FoxAblazeUltimateMod.MOD_ID, "textures/gui/beelzebub/spatial.png");
    private static final ResourceLocation WATER_BAR = ResourceLocation.fromNamespaceAndPath(
            FoxAblazeUltimateMod.MOD_ID, "textures/gui/beelzebub/water_bar.png");
    private static final ResourceLocation LAVA_BAR = ResourceLocation.fromNamespaceAndPath(
            FoxAblazeUltimateMod.MOD_ID, "textures/gui/beelzebub/lava_bar.png");

    /**
     * 水/岩浆条几何常量。与 {@link com.foxablazeultimate.menu.BeelzebubStorageMenu} 中的
     * {@code BUCKET_INPUT_X/Y} 保持对齐：bars 贴在 input slot 的两侧，高度跨越 input + output 两个 slot。
     * <p>贴图本身是 10×122（与 Tensura 原版同源的"empty top + filled gradient"布局），这里需要用 11 参 blit 压到
     * GUI {@code BAR_H} 高。最终在屏上占 {@code BAR_W × BAR_H} 像素。
     */
    private static final int WATER_BAR_X = 186, WATER_BAR_Y = 123;
    private static final int LAVA_BAR_X  = 234, LAVA_BAR_Y  = 123;
    private static final int BAR_W = 10, BAR_H = 69; // 高度 = 输出槽底(174+18) - bar 顶(123) = 69
    private static final int TEX_H = 122;            // 贴图原始高（与 Gluttony 同源）

    private final int maxSize;
    public final int page;

    public BeelzebubStorageScreen(BeelzebubStorageMenu menu, Inventory inv, int maxSize, int page) {
        // ★ 标题用"虚数空间"（mode 1 名），而不是技能名"暴食之王·别西卜"。
        // 复用 lang 中现有的 foxablazeultimate.skill.mode.beelzebub.stomach 键。
        super(menu, inv, Component.translatable("foxablazeultimate.skill.mode.beelzebub.stomach"));
        this.maxSize = maxSize;
        this.page = page;
        this.imageWidth = 256;
        this.imageHeight = 201;
    }

    @Override
    protected void init() {
        super.init();
        // 暴食之王 GUI title 在 y≈31，按钮放 y=2 的标题栏上方空白区
        PredationFilterOverlay.onScreenInit(2);
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float f) {
        super.render(graphics, x, y, f);
        this.renderTooltip(graphics, x, y);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int i, int j) {
        // 与 Researcher 一致：x=88 为虚数区中心；title 居中靠虚数区，playerInventoryTitle 居中靠玩家区
        RenderHelper.drawCenteredText(guiGraphics, this.font, this.title,
                88, this.titleLabelY + 25, Color.WHITE.getRGB(), false);
        RenderHelper.drawCenteredText(guiGraphics, this.font, this.playerInventoryTitle,
                88, this.inventoryLabelY + 35, 4210752, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float f, int x, int y) {
        int width = (this.width - this.imageWidth) / 2;
        int height = (this.height - this.imageHeight) / 2;
        // 7 参数 blit（默认 textureSize=256），与 Researcher 完全等价
        graphics.blit(BACKGROUND, width, height, 0, 0, 256, this.imageHeight);

        // v2: 水/岩浆条（76 in `BUCKET_INPUT/OUTPUT` 区域两侧）
        renderFluidBar(graphics, WATER_BAR, WATER_BAR_X, WATER_BAR_Y,
                getStorageOwnerWaterPoint(), getStorageOwnerWaterCapacity());
        renderFluidBar(graphics, LAVA_BAR, LAVA_BAR_X, LAVA_BAR_Y,
                getStorageOwnerLavaPoint(), getStorageOwnerLavaCapacity());

        // 空 slot 黑底覆盖：与 Researcher 完全一致，源贴图取 (0, imageHeight) 处 18×18 块
        int size = this.maxSize - 27 * this.page;
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                if (size <= 0) {
                    graphics.blit(BACKGROUND, width + 7 + j * 18, height + 43 + i * 18,
                            0, this.imageHeight, 18, 18);
                }
                --size;
            }
        }

        // 翻页箭头：与 Researcher 完全一致 (7, 99) / (151, 99)
        if (this.page > 0) {
            boolean hovering = x >= width + 7 && x < width + 25 && y >= height + 99 && y < height + 109;
            graphics.blit(BACKGROUND, width + 7, height + 99,
                    0, this.imageHeight + (hovering ? 28 : 18), 18, 10);
        }
        if (this.page < (this.maxSize - 1) / 27) {
            boolean hovering = x >= width + 151 && x < width + 169 && y >= height + 99 && y < height + 109;
            graphics.blit(BACKGROUND, width + 151, height + 99,
                    18, this.imageHeight + (hovering ? 28 : 18), 18, 10);
        }
        // 不渲染 ENDER_CHEST / ENCHANTING_TABLE 图标（暴食之王无附魔切换 tab）
        // 拉斐尔捕食过滤器按钮 + 展开行（仅背景， item 走 renderItems）。
        PredationFilterOverlay.render(graphics, this.leftPos, this.topPos, this.imageWidth, x, y);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        super.renderTooltip(graphics, x, y);
        int width = (this.width - this.imageWidth) / 2;
        int height = (this.height - this.imageHeight) / 2;
        if (x >= width + 7 && x < width + 25 && y >= height + 99 && y < height + 109 && this.page > 0) {
            graphics.renderTooltip(this.font, Component.translatable("tooltip.tensura.kiln.mixing_left"), x, y);
        } else if (x >= width + 151 && x < width + 169 && y >= height + 99 && y < height + 109
                && this.page < (this.maxSize - 1) / 27) {
            graphics.renderTooltip(this.font, Component.translatable("tooltip.tensura.kiln.mixing_right"), x, y);
        } else if (mouseOverBar(x, y, WATER_BAR_X, WATER_BAR_Y)) {
            double pt = getStorageOwnerWaterPoint();
            double max = getStorageOwnerWaterCapacity();
            graphics.renderTooltip(this.font, java.util.List.of(
                    Component.literal(pt + "/" + max).withStyle(ChatFormatting.AQUA).getVisualOrderText(),
                    Component.translatable("foxablazeultimate.beelzebub.fluid.extract_hint_water")
                            .withStyle(ChatFormatting.GRAY).getVisualOrderText()),
                    x, y);
        } else if (mouseOverBar(x, y, LAVA_BAR_X, LAVA_BAR_Y)) {
            double pt = getStorageOwnerLavaPoint();
            double max = getStorageOwnerLavaCapacity();
            graphics.renderTooltip(this.font, java.util.List.of(
                    Component.literal(pt + "/" + max).withStyle(ChatFormatting.RED).getVisualOrderText(),
                    Component.translatable("foxablazeultimate.beelzebub.fluid.extract_hint_lava")
                            .withStyle(ChatFormatting.GRAY).getVisualOrderText()),
                    x, y);
        }
        PredationFilterOverlay.renderTooltip(graphics, this.leftPos, this.topPos, this.imageWidth, x, y);
        // 不显示 storage_tab / enchantment_tab tooltip
    }

    // ===========================================================
    // |              v2 水/岩浆条渲染与 tooltip                |
    // ===========================================================

    /**
     * 渲染一根水/岩浆条。贴图是 10×122 「empty top + filled bottom gradient」（与 Tensura 同源格式），这里
     * 采用 11 参 {@code blit} 把它压到 {@link #BAR_H}。渲染逻辑：
     * <ol>
     *   <li>{@code ratio = pt/max}（clamp 到 [0, 1]）</li>
     *   <li>GUI 充充高度 = {@code BAR_H × ratio}，从底向上填</li>
     *   <li>贴图采样高度 = {@code TEX_H × ratio}，从贴图底部向上取（v offset = TEX_H - sample）</li>
     *   <li>贴图上半部分（empty）不需要画——背景上本来就有「空条」的外框贴图</li>
     * </ol>
     */
    private void renderFluidBar(GuiGraphics graphics, ResourceLocation tex,
                                 int barX, int barY, double point, double max) {
        if (max <= 0.0) return;
        double ratio = Mth.clamp(point / max, 0.0, 1.0);
        int fillGui = (int) Math.round(BAR_H * ratio);
        if (fillGui <= 0) return;
        int fillTex = (int) Math.round(TEX_H * ratio);
        if (fillTex <= 0) return;

        int emptyGui = BAR_H - fillGui;
        int emptyTex = TEX_H - fillTex;
        int pX = this.leftPos + barX;
        int pY = this.topPos + barY + emptyGui;
        graphics.blit(tex,
                pX, pY,
                BAR_W, fillGui,            // GUI render 尺寸（可能与 texture 采样尺寸不同 → 缩放）
                0.0F, (float) emptyTex,    // texture 采样起点 (u, v)
                BAR_W, fillTex,            // texture 采样尺寸
                BAR_W, TEX_H);             // texture 总尺寸
    }

    /** 点更 (mx, my) 是否在指定 bar GUI 区域内。 */
    private boolean mouseOverBar(int mx, int my, int barX, int barY) {
        return RenderHelper.mouseOver((double) mx, (double) my,
                (double) (this.leftPos + barX), (double) (this.leftPos + barX + BAR_W),
                (double) (this.topPos + barY), (double) (this.topPos + barY + BAR_H));
    }

    // ====== 存储主身 water/lava capacity 与当前点数获取 ======

    private double getStorageOwnerWaterPoint() {
        return TensuraStorages.getAbilityFrom(this.menu.getStorageOwner()).getWaterPoint();
    }
    private double getStorageOwnerLavaPoint() {
        return TensuraStorages.getAbilityFrom(this.menu.getStorageOwner()).getLavaPoint();
    }
    private double getStorageOwnerWaterCapacity() {
        return this.menu.getStorageOwner().getAttributeValue(TensuraAttributes.WATER_CAPACITY);
    }
    private double getStorageOwnerLavaCapacity() {
        return this.menu.getStorageOwner().getAttributeValue(TensuraAttributes.LAVA_CAPACITY);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int leftPos, int topPos, int button) {
        // overlay \u533a\u57df\uff08\u6309\u94ae + \u5c55\u5f00\u884c\uff09\u4e0d\u7b97\u201c\u5916\u90e8\u201d\uff0c\u8fd9\u6837 mouseReleased \u4e0d\u4f1a\u5f53\u4f5c\u6254\u7269\u54c1\u5904\u7406\u3002
        if (PredationFilterOverlay.isMouseOverOverlay(mouseX, mouseY, this.leftPos, this.topPos, this.imageWidth)) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, leftPos, topPos, button);
    }

    @Override
    public boolean mouseClicked(double x, double y, int i) {
        if (this.minecraft == null) return false;

        int width = (this.width - this.imageWidth) / 2;
        int height = (this.height - this.imageHeight) / 2;
        if (PredationFilterOverlay.mouseClicked(x, y, i, this.leftPos, this.topPos, this.imageWidth)) {
            return true;
        }

        // v3: 右键水/熔岩条 → 发 C2S 请求抽取液体（只在<b>右键</b>处理；左键保留为后续可能的 tooltip 锁定等扩展）
        if (i == 1 && this.minecraft.player != null) {
            byte fluidType = -1;
            if (mouseOverBar((int) x, (int) y, WATER_BAR_X, WATER_BAR_Y)) {
                fluidType = RequestBeelzebubFluidExtractPayload.FLUID_WATER;
            } else if (mouseOverBar((int) x, (int) y, LAVA_BAR_X, LAVA_BAR_Y)) {
                fluidType = RequestBeelzebubFluidExtractPayload.FLUID_LAVA;
            }
            if (fluidType >= 0) {
                NetworkManager.sendToServer(new RequestBeelzebubFluidExtractPayload(
                        this.menu.containerId, fluidType));
                // 客户端不预播音效，由服务端 playSound 推回，避免空抽时仍发"装填"音
                return true;
            }
        }

        // 上一页
        if (x >= width + 7 && x < width + 25 && y >= height + 99 && y < height + 109
                && this.page > 0
                && this.menu.clickMenuButton(this.minecraft.player, 0)) {
            if (this.minecraft.gameMode == null) return false;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
            return true;
        }
        // 下一页
        if (x >= width + 151 && x < width + 169 && y >= height + 99 && y < height + 109
                && this.page < (this.maxSize - 1) / 27
                && this.menu.clickMenuButton(this.minecraft.player, 1)) {
            if (this.minecraft.gameMode == null) return false;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 1);
            return true;
        }
        // 不处理 i == -1（无附魔切换 tab）
        return super.mouseClicked(x, y, i);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.minecraft != null && this.minecraft.options.keySwapOffhand.matches(keyCode, scanCode)
                || super.keyPressed(keyCode, scanCode, modifiers);
    }
}
