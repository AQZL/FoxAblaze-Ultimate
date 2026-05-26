package com.foxablazeultimate.event;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.registry.FoxAblazeUltimateItems;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@EventBusSubscriber(modid = FoxAblazeUltimateMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class FoxAblazeCreativeTabHandler {

    private FoxAblazeCreativeTabHandler() {}

    @SubscribeEvent
    public static void onBuildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(FoxAblazeUltimateItems.WISDOM_CRYSTAL.get());
        }
    }
}
