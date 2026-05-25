package com.foxablazeultimate.registry;

import com.foxablazeultimate.FoxAblazeUltimateMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FoxAblazeUltimateSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, FoxAblazeUltimateMod.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> RAPHAEL_FUSION_DECLARATION =
            SOUND_EVENTS.register("raphael.fusion_declaration",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(
                                    FoxAblazeUltimateMod.MOD_ID, "raphael.fusion_declaration")));

    public static final DeferredHolder<SoundEvent, SoundEvent> BEELZEBUB_FUSION_DECLARATION =
            SOUND_EVENTS.register("beelzebub.fusion_declaration",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(
                                    FoxAblazeUltimateMod.MOD_ID, "beelzebub.fusion_declaration")));

    public static final DeferredHolder<SoundEvent, SoundEvent> URIEL_SKILL_DECLARATION =
            SOUND_EVENTS.register("uriel.skill_declaration",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(
                                    FoxAblazeUltimateMod.MOD_ID, "uriel.skill_declaration")));

    private FoxAblazeUltimateSounds() {}

    public static void register(IEventBus modBus) {
        SOUND_EVENTS.register(modBus);
    }
}
