package com.foxablazeultimate.item.beelzebub;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 被别西卜虚数胃袋捕获的实体快照。
 * <p>承载于 {@link com.foxablazeultimate.item.beelzebub.CapturedEntityItem CapturedEntityItem} 的 DataComponent 上，
 * 包含还原实体所需的全部信息：
 * <ul>
 *   <li>{@code entityType}：实体注册名（用于 {@code EntityType.byString} 反序列化）</li>
 *   <li>{@code entityNbt}：完整 {@code Entity#save} 输出（HP / 装备 / AI / 经验 / 名字 / 自定义 NBT）</li>
 *   <li>{@code displayName}：捕获瞬间的显示名（避免每次 hover 都解析 NBT，也防止反序列化失败时无名可显示）</li>
 *   <li>{@code cachedEP}：捕获瞬间的 EnergyHelper.getMaxEP 快照（tooltip 展示）</li>
 *   <li>{@code captureTimeMillis}：服务端 System.currentTimeMillis()，配合配置项 {@code captureFreezeTicks} 让被捕实体的 tickCount 在释放时回滚</li>
 * </ul>
 *
 * <p>所有字段均为 immutable record；任何修改请走 with* / 重新构造。
 */
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
