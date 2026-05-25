package com.foxablazeultimate.client;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.client.render.CapturedEntityRenderer;
import com.foxablazeultimate.registry.FoxAblazeUltimateItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = FoxAblazeUltimateMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FoxAblazeUltimateModClient {

    private static CapturedEntityRenderer capturedEntityRendererInstance;

    private FoxAblazeUltimateModClient() {}

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (capturedEntityRendererInstance == null) {
                    Minecraft mc = Minecraft.getInstance();
                    capturedEntityRendererInstance = new CapturedEntityRenderer(
                            mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
                }
                return capturedEntityRendererInstance;
            }
        }, FoxAblazeUltimateItems.CAPTURED_ENTITY.get());
    }
}
