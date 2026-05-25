package com.foxablazeultimate.registry;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.item.beelzebub.CapturedEntityItem;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FoxAblazeUltimateItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(FoxAblazeUltimateMod.MOD_ID);

    public static final DeferredHolder<Item, CapturedEntityItem> CAPTURED_ENTITY =
            ITEMS.register("captured_entity",
                    () -> new CapturedEntityItem(new Item.Properties()));

    private FoxAblazeUltimateItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
