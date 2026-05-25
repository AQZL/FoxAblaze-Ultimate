package com.foxablazeultimate.client.screen;

import java.awt.Color;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.menu.RaphaelNamingMenu;
import com.foxablazeultimate.network.RequestRaphaelRenamePayload;

import dev.architectury.networking.NetworkManager;
import io.github.manasmods.tensura.util.client.RenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

/**
 * 智慧之王 · 拉斐尔 · 命名 GUI（Tensura 原版 NamingScreen 的简化版）。
 *
 * <h3>布局</h3>
 * <p>直接复用 Tensura 原版命名 GUI 背景贴图 {@code tensura:textures/gui/naming/naming_gui.png}（144×96），
 * 但仅渲染：
 * <ul>
 *   <li>顶部 EditBox：玩家输入想给拉斐尔起的"专属名"，初始值为服务端推送的<b>默认名 lang key 在客户端翻译后的结果</b></li>
 *   <li>右上骰子按钮：把 EditBox 重置为<b>默认名</b>（注意：与 Tensura 原版不同——原版是"随机一个名字"，
 *       我们这里没有 NamingMenu 配置中的随机名表，"重置"是更贴合"恢复默认拉斐尔"语义的行为）</li>
 *   <li>底部确认按钮：发送 C2S 重命名 payload 并关闭 GUI</li>
 * </ul>
 *
 * <h3>不渲染</h3>
 * <p>原版 (subdue / evolve / endow) 三个类型按钮（位于贴图中段 y=47..68，三横列各 21×21）<b>完全不画也不响应点击</b>，
 * 玩家看到的是"光秃秃的中段"——这正是用户要的"无赋予 / 进化等三按钮"形态。
 *
 * <h3>状态语义</h3>
 * <p>本 Screen 不需要 type 选择步骤；点击确认时只要 EditBox 非空白就发包。空白 → 重置为默认名（视作放弃命名，
 * 服务端会清空 RaphaelCustomName）。
 *
 * <h3>i18n</h3>
 * <p>服务端无法解析 lang key（{@code Component.translatable("...").getString()} 在专用服务器只会返回 key 本身）。
 * 因此 payload 携带的是 <b>lang key 字符串</b>（如 "foxablazeultimate.skill.raphael"），由本 Screen 在客户端
 * 用 {@code Component.translatable(key).getString()} 翻成本地化字符串后再写进 EditBox。
 */
public class RaphaelNamingScreen extends AbstractContainerScreen<RaphaelNamingMenu> {

    /** Fox Ablaze Ultimate 命名 GUI 背景（用户提供的 mmxt.png）。布局尺寸沿用 Tensura 144×96。 */
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(FoxAblazeUltimateMod.MOD_ID, "textures/gui/raphael/naming.png");

    /** 客户端翻译后的默认名（"智慧之王·拉斐尔" / "Wisdom King · Raphael"）。 */
    private final String defaultName;
    private EditBox editBox;

    public RaphaelNamingScreen(RaphaelNamingMenu menu, Inventory inventory, String defaultNameLangKey) {
        super(menu, inventory, Component.translatable("foxablazeultimate.skill.raphael.naming.title"));
        this.imageWidth = 144;
        this.imageHeight = 96;
        // 在客户端解析 lang key；若 key 直接传成普通字符串（fallback），translatable 会原样保留
        this.defaultName = Component.translatable(defaultNameLangKey).getString();
    }

    @Override
    protected void init() {
        super.init();
        this.editBox = new EditBox(this.font, this.leftPos + 19, this.topPos + 27, 85, 11, Component.empty());
        this.editBox.setBordered(false);
        this.editBox.setMaxLength(64);
        this.editBox.setValue(this.defaultName);
        this.addRenderableWidget(this.editBox);
        // 直接焦点到输入框，玩家进 GUI 就能输入
        this.setInitialFocus(this.editBox);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float particleTick, int pX, int pY) {
        graphics.blit(BACKGROUND, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);

        // 底部确认按钮 hover 高亮（沿用原版位置 37..107 × 74..89）
        if (RenderHelper.mouseOver((double) pX, (double) pY,
                (double) (this.leftPos + 36), (double) (this.leftPos + 107),
                (double) (this.topPos + 73), (double) (this.topPos + 89))) {
            graphics.blit(BACKGROUND, this.leftPos + 37, this.topPos + 74, 1.0F, 176.0F, 70, 15, 256, 256);
        }

        // 右上骰子按钮 hover 高亮（沿用原版位置 113..128 × 22..39）
        if (RenderHelper.mouseOver((double) pX, (double) pY,
                (double) (this.leftPos + 113), (double) (this.leftPos + 128),
                (double) (this.topPos + 22), (double) (this.topPos + 39))) {
            graphics.blit(BACKGROUND, this.leftPos + 114, this.topPos + 23, 79.0F, 97.0F, 14, 16, 256, 256);
        }

        // 底部按钮文字"name"，与原版同 i18n 键
        RenderHelper.drawCenteredText(graphics, this.font,
                Component.translatable("tensura.naming.name"),
                this.leftPos + 72, this.topPos + 78, Color.WHITE.getRGB(), false);

        // ★ 不渲染 3 个类型按钮 (subdue / evolve / endow) —— 这正是与原版的核心差异
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int pX, int pY) {
        // 骰子按钮 tooltip：用我们自己的"reset to default"翻译键，保持语义清晰
        if (RenderHelper.mouseOver((double) pX, (double) pY,
                (double) (this.leftPos + 114), (double) (this.leftPos + 128),
                (double) (this.topPos + 23), (double) (this.topPos + 39))) {
            graphics.renderTooltip(this.font,
                    Component.translatable("foxablazeultimate.skill.raphael.naming.reset"), pX, pY);
        }
        super.renderTooltip(graphics, pX, pY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 与原版一致：背景已自带框，不再画外部标题；交给 renderBg 中的 drawCenteredText
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        // 骰子按钮 → 重置为默认名（不做"随机名表"，与原版区别在此）
        if (RenderHelper.mouseOver(pMouseX, pMouseY,
                (double) (this.leftPos + 114), (double) (this.leftPos + 128),
                (double) (this.topPos + 23), (double) (this.topPos + 39))) {
            playClick();
            this.editBox.setValue(this.defaultName);
            this.editBox.setCursorPosition(0);
            this.editBox.setHighlightPos(0);
            this.editBox.setFocused(false);
            return true;
        }

        // 底部确认按钮
        if (RenderHelper.mouseOver(pMouseX, pMouseY,
                (double) (this.leftPos + 37), (double) (this.leftPos + 107),
                (double) (this.topPos + 74), (double) (this.topPos + 89))) {
            playClick();
            String value = this.editBox.getValue();
            if (value == null || value.isBlank()) {
                // 空白 → 视作"清除自定义名，恢复默认"
                value = "";
            } else if (value.equals(this.defaultName)) {
                // 等于默认名 → 也清掉自定义记录，避免持久化无意义的字符串
                value = "";
            }
            NetworkManager.sendToServer(new RequestRaphaelRenamePayload(value));
            this.onClose();
            return true;
        }

        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
        // 输入框处理优先；E（背包键）在 EditBox 聚焦时被截断，避免按 E 关 Screen 同时把字母 e 输进框里
        if (this.editBox.keyPressed(pKeyCode, pScanCode, pModifiers)) return true;
        if (this.editBox.isFocused() && this.editBox.isVisible() && pKeyCode != 256) return true;

        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(pKeyCode, pScanCode)) {
            return true;
        }
        return super.keyPressed(pKeyCode, pScanCode, pModifiers);
    }

    /** 所有按钮统一的 click 音；UI_BUTTON_CLICK 在 Tensura 原版 NamingScreen 也是这个。 */
    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
