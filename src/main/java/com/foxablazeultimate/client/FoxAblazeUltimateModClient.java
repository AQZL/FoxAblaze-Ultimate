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

/**
 * Fox Ablaze Ultimate 客户端入口。
 * <p>仅在客户端环境中加载（{@link Dist#CLIENT}）；负责注册 BEWLR、客户端 only 的 ResourceLocation 资源等。
 * 与 mod 主类分离：避免在专用服务器加载时触摸客户端类引发 ClassNotFoundException。
 */
@EventBusSubscriber(modid = FoxAblazeUltimateMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FoxAblazeUltimateModClient {

    private static CapturedEntityRenderer capturedEntityRendererInstance;

    private FoxAblazeUltimateModClient() {}

    /**
     * 给 {@link FoxAblazeUltimateItems#CAPTURED_ENTITY} 绑定自家 BEWLR。
     * <p>BEWLR 实例必须在 {@code Minecraft.getInstance().getBlockEntityRenderDispatcher()} 与
     * {@code getEntityModels()} 已就绪时构造；本事件在 mod 加载靠后阶段发出，已满足条件。
     */
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
