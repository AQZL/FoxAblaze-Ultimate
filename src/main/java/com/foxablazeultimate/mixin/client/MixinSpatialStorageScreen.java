package com.foxablazeultimate.mixin.client;

import com.foxablazeultimate.client.screen.PredationFilterOverlay;

import io.github.manasmods.tensura.client.screen.SpatialStorageScreen;
import io.github.manasmods.tensura.menu.SpatialStorageMenu;
import io.github.manasmods.tensura.registry.skill.UniqueSkills;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给 Tensura 原版「暴食 Gluttony」虚数仓库 GUI 注入拉斐尔捕食过滤器叠加层。
 *
 * <p>只针对 {@code UniqueSkills.GLUTTONY}；别西卜 GUI 走自家的
 * {@link com.foxablazeultimate.client.screen.BeelzebubStorageScreen}，已直接在屏幕代码里调用 overlay。
 *
 * <p>注入点：
 * <ul>
 *   <li>{@code init} TAIL：触发 overlay 请求服务端同步当前过滤器内容；</li>
 *   <li>{@code renderBg} TAIL：画按钮和（展开时）行背景；</li>
 *   <li>{@code render} TAIL：把展开行里的鬼影 item 画到所有 GUI 元素之上；</li>
 *   <li>{@code renderTooltip} TAIL：处理按钮 / 槽位 tooltip；</li>
 *   <li>{@code mouseClicked} HEAD：按钮切换 / 鬼影写入。</li>
 * </ul>
 */
@Mixin(value = SpatialStorageScreen.class, remap = false)
public abstract class MixinSpatialStorageScreen extends AbstractContainerScreen<SpatialStorageMenu> {
    private MixinSpatialStorageScreen(SpatialStorageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private boolean foxablazeultimate$isGluttony() {
        return this.menu.getSkill() == UniqueSkills.GLUTTONY.get();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void foxablazeultimate$initFilter(CallbackInfo ci) {
        if (!foxablazeultimate$isGluttony()) return;
        // 暴食者 GUI title 在 y≈11，按钮放 y=-21（按钮底边刚好贴在 GUI 顶边，整块按钮浮在 GUI 上方）
        PredationFilterOverlay.onScreenInit(-21);
    }

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void foxablazeultimate$renderFilter(GuiGraphics graphics, float partialTick, int mouseX, int mouseY,
                                                CallbackInfo ci) {
        if (!foxablazeultimate$isGluttony()) return;
        PredationFilterOverlay.render(graphics, this.leftPos, this.topPos, this.imageWidth, mouseX, mouseY);
    }

    @Inject(method = "renderTooltip", at = @At("TAIL"))
    private void foxablazeultimate$renderFilterTooltip(GuiGraphics graphics, int mouseX, int mouseY,
                                                       CallbackInfo ci) {
        if (!foxablazeultimate$isGluttony()) return;
        PredationFilterOverlay.renderTooltip(graphics, this.leftPos, this.topPos, this.imageWidth, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void foxablazeultimate$clickFilter(double mouseX, double mouseY, int button,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (!foxablazeultimate$isGluttony()) return;
        if (PredationFilterOverlay.mouseClicked(mouseX, mouseY, button, this.leftPos, this.topPos, this.imageWidth)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 直接覆写 {@code hasClickedOutside}，<b>不</b>用 {@code @Inject}。
     *
     * <p>原因：{@link SpatialStorageScreen} 自己没有 override {@code hasClickedOutside}，只继承自
     * {@link AbstractContainerScreen}。Mixin {@code @Inject} 必须能在目标类自身的 bytecode 中找到方法，
     * 否则 apply 时抛错导致整个 mixin 失败 → 屏幕打不开。<br>
     * Mixin 会自动把这里的普通方法合并到目标类作为新方法/override，{@code super.hasClickedOutside}
     * 调用会正确解析为父类版本。
     *
     * <p>语义：overlay 区域（按钮 + 展开行）不算"外部"，避免 vanilla {@code mouseReleased}
     * 在那里把 carry 物品当作丢弃处理。
     */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int leftPos, int topPos, int button) {
        if (foxablazeultimate$isGluttony()
                && PredationFilterOverlay.isMouseOverOverlay(mouseX, mouseY, this.leftPos, this.topPos, this.imageWidth)) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, leftPos, topPos, button);
    }
}
