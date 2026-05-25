package com.foxablazeultimate.network;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.menu.BeelzebubStorageMenu;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record RequestBeelzebubFluidExtractPayload(
        int containerId,
        byte fluidType) implements CustomPacketPayload {

    public static final byte FLUID_WATER = 0;
    public static final byte FLUID_LAVA  = 1;

    public static final CustomPacketPayload.Type<RequestBeelzebubFluidExtractPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    FoxAblazeUltimateMod.MOD_ID, "request_beelzebub_fluid_extract"));

    public static final StreamCodec<FriendlyByteBuf, RequestBeelzebubFluidExtractPayload> STREAM_CODEC =
            CustomPacketPayload.codec(RequestBeelzebubFluidExtractPayload::encode, RequestBeelzebubFluidExtractPayload::new);

    public RequestBeelzebubFluidExtractPayload(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readByte());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.containerId);
        buf.writeByte(this.fluidType);
    }

    public static void handle(RequestBeelzebubFluidExtractPayload payload, NetworkManager.PacketContext context) {

        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
        if (payload.fluidType != FLUID_WATER && payload.fluidType != FLUID_LAVA) return;

        context.queue(() -> {

            if (!(player.containerMenu instanceof BeelzebubStorageMenu menu)) return;
            if (menu.containerId != payload.containerId) return;
            menu.extractFluid(player, payload.fluidType);
        });
    }

    @Override
    public CustomPacketPayload.Type<RequestBeelzebubFluidExtractPayload> type() {
        return TYPE;
    }
}
