package com.foxablazeultimate.registry;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.item.beelzebub.CapturedEntityData;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FoxAblazeUltimateDataComponents {

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FoxAblazeUltimateMod.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CapturedEntityData>> CAPTURED_ENTITY =
            DATA_COMPONENTS.registerComponentType("captured_entity",
                    builder -> builder
                            .persistent(CapturedEntityData.CODEC)
                            .networkSynchronized(CapturedEntityData.STREAM_CODEC));

    private FoxAblazeUltimateDataComponents() {}

    public static void register(IEventBus modBus) {
        DATA_COMPONENTS.register(modBus);
    }
}
