package com.foxablazeultimate.registry;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.item.beelzebub.CapturedEntityItem;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Fox Ablaze Ultimate 自定义物品注册表。
 * <p>目前仅包含别西卜捕获实体形态物品。
 */
public final class FoxAblazeUltimateItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(FoxAblazeUltimateMod.MOD_ID);

    /**
     * 暴食之王捕获实体形态物品。
     * <p>不进创造模式物品栏：通过指定 ID 但不绑定到 CreativeModeTab 实现，避免 /give 拿到空白 stub 卡死系统。
     */
    public static final DeferredHolder<Item, CapturedEntityItem> CAPTURED_ENTITY =
            ITEMS.register("captured_entity",
                    () -> new CapturedEntityItem(new Item.Properties()));

    private FoxAblazeUltimateItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
