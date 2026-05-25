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

public class BeelzebubStorageScreen extends AbstractContainerScreen<BeelzebubStorageMenu> {

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            FoxAblazeUltimateMod.MOD_ID, "textures/gui/beelzebub/spatial.png");
    private static final ResourceLocation WATER_BAR = ResourceLocation.fromNamespaceAndPath(
            FoxAblazeUltimateMod.MOD_ID, "textures/gui/beelzebub/water_bar.png");
    private static final ResourceLocation LAVA_BAR = ResourceLocation.fromNamespaceAndPath(
            FoxAblazeUltimateMod.MOD_ID, "textures/gui/beelzebub/lava_bar.png");

    private static final int WATER_BAR_X = 186, WATER_BAR_Y = 123;
    private static final int LAVA_BAR_X  = 234, LAVA_BAR_Y  = 123;
    private static final int BAR_W = 10, BAR_H = 69; 
    private static final int TEX_H = 122;            

    private final int maxSize;
    public final int page;

    public BeelzebubStorageScreen(BeelzebubStorageMenu menu, Inventory inv, int maxSize, int page) {
        super(menu, inv, Component.translatable("foxablazeultimate.skill.mode.beelzebub.stomach"));
        this.maxSize = maxSize;
        this.page = page;
        this.imageWidth = 256;
        this.imageHeight = 201;
    }

    @Override
    protected void init() {
        super.init();
        PredationFilterOverlay.onScreenInit(2);
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float f) {
        super.render(graphics, x, y, f);
        this.renderTooltip(graphics, x, y);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int i, int j) {
        RenderHelper.drawCenteredText(guiGraphics, this.font, this.title,
                88, this.titleLabelY + 25, Color.WHITE.getRGB(), false);
        RenderHelper.drawCenteredText(guiGraphics, this.font, this.playerInventoryTitle,
                88, this.inventoryLabelY + 35, 4210752, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float f, int x, int y) {
        int width = (this.width - this.imageWidth) / 2;
        int height = (this.height - this.imageHeight) / 2;
        graphics.blit(BACKGROUND, width, height, 0, 0, 256, this.imageHeight);

        renderFluidBar(graphics, WATER_BAR, WATER_BAR_X, WATER_BAR_Y,
                getStorageOwnerWaterPoint(), getStorageOwnerWaterCapacity());
        renderFluidBar(graphics, LAVA_BAR, LAVA_BAR_X, LAVA_BAR_Y,
                getStorageOwnerLavaPoint(), getStorageOwnerLavaCapacity());

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
    }


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
                BAR_W, fillGui,            
                0.0F, (float) emptyTex,    
                BAR_W, fillTex,            
                BAR_W, TEX_H);             
    }

    private boolean mouseOverBar(int mx, int my, int barX, int barY) {
        return RenderHelper.mouseOver((double) mx, (double) my,
                (double) (this.leftPos + barX), (double) (this.leftPos + barX + BAR_W),
                (double) (this.topPos + barY), (double) (this.topPos + barY + BAR_H));
    }


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
                return true;
            }
        }

        if (x >= width + 7 && x < width + 25 && y >= height + 99 && y < height + 109
                && this.page > 0
                && this.menu.clickMenuButton(this.minecraft.player, 0)) {
            if (this.minecraft.gameMode == null) return false;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
            return true;
        }
        if (x >= width + 151 && x < width + 169 && y >= height + 99 && y < height + 109
                && this.page < (this.maxSize - 1) / 27
                && this.menu.clickMenuButton(this.minecraft.player, 1)) {
            if (this.minecraft.gameMode == null) return false;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 1);
            return true;
        }
        return super.mouseClicked(x, y, i);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.minecraft != null && this.minecraft.options.keySwapOffhand.matches(keyCode, scanCode)
                || super.keyPressed(keyCode, scanCode, modifiers);
    }
}
