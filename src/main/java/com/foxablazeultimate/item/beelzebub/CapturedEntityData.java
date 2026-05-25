package com.foxablazeultimate.item.beelzebub;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record CapturedEntityData(
        ResourceLocation entityType,
        CompoundTag entityNbt,
        String displayName,
        double cachedEP,
        long captureTimeMillis) {

    public static final Codec<CapturedEntityData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ResourceLocation.CODEC.fieldOf("entity_type").forGetter(CapturedEntityData::entityType),
            CompoundTag.CODEC.fieldOf("entity_nbt").forGetter(CapturedEntityData::entityNbt),
            Codec.STRING.fieldOf("display_name").forGetter(CapturedEntityData::displayName),
            Codec.DOUBLE.fieldOf("cached_ep").forGetter(CapturedEntityData::cachedEP),
            Codec.LONG.fieldOf("capture_time").forGetter(CapturedEntityData::captureTimeMillis)
    ).apply(inst, CapturedEntityData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CapturedEntityData> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, CapturedEntityData::entityType,
            ByteBufCodecs.TRUSTED_COMPOUND_TAG, CapturedEntityData::entityNbt,
            ByteBufCodecs.STRING_UTF8, CapturedEntityData::displayName,
            ByteBufCodecs.DOUBLE, CapturedEntityData::cachedEP,
            ByteBufCodecs.VAR_LONG, CapturedEntityData::captureTimeMillis,
            CapturedEntityData::new
    );
}
