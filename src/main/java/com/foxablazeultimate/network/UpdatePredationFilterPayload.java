package com.foxablazeultimate.network;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.predation.PredationFilterHelper;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * C2S：客户端在过滤器叠加层点击某个过滤槽时发包，把这一槽的"鬼影物品"写入服务端。
 *
 * <p>{@code ghost} 永远是数量被截到 1 的副本；空 {@link ItemStack#EMPTY} 表示清空该槽。
 * 服务端校验玩家是否仍持有拉斐尔后写入 NBT，并通过 {@link SyncPredationFilterPayload} 广播完整状态。
 */
public record UpdatePredationFilterPayload(int slot, ItemStack ghost) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UpdatePredationFilterPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    FoxAblazeUltimateMod.MOD_ID, "update_predation_filter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdatePredationFilterPayload> STREAM_CODEC =
            CustomPacketPayload.codec(UpdatePredationFilterPayload::encode, UpdatePredationFilterPayload::new);

    public UpdatePredationFilterPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.slot);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, this.ghost);
    }

    public static void handle(UpdatePredationFilterPayload payload, NetworkManager.PacketContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
        context.queue(() -> PredationFilterHelper.setSlot(player, payload.slot,
                payload.ghost.isEmpty() ? ItemStack.EMPTY : payload.ghost.copyWithCount(1)));
    }

    @Override
    public CustomPacketPayload.Type<UpdatePredationFilterPayload> type() {
        return TYPE;
    }
}
