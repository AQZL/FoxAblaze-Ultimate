package com.foxablazeultimate.registry;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.loot.AddWisdomCrystalLootModifier;
import com.mojang.serialization.MapCodec;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class FoxAblazeUltimateLootModifiers {

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
                    FoxAblazeUltimateMod.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>,
            MapCodec<AddWisdomCrystalLootModifier>> ADD_WISDOM_CRYSTAL =
            LOOT_MODIFIER_SERIALIZERS.register("add_wisdom_crystal",
                    () -> AddWisdomCrystalLootModifier.CODEC);

    private FoxAblazeUltimateLootModifiers() {}

    public static void register(IEventBus modBus) {
        LOOT_MODIFIER_SERIALIZERS.register(modBus);
    }
}
