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

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int leftPos, int topPos, int button) {
        if (foxablazeultimate$isGluttony()
                && PredationFilterOverlay.isMouseOverOverlay(mouseX, mouseY, this.leftPos, this.topPos, this.imageWidth)) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, leftPos, topPos, button);
    }
}
