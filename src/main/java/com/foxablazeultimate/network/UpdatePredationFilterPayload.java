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
