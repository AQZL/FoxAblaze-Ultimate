package com.foxablazeultimate.registry;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.effect.WisdomBuffEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FoxAblazeUltimateMobEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, FoxAblazeUltimateMod.MOD_ID);

    public static final DeferredHolder<MobEffect, WisdomBuffEffect> WISDOM_BUFF =
            MOB_EFFECTS.register("wisdom_buff", WisdomBuffEffect::new);

    private FoxAblazeUltimateMobEffects() {}

    public static void register(IEventBus modBus) {
        MOB_EFFECTS.register(modBus);
    }
}
