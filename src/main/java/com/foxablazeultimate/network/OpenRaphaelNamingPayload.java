package com.foxablazeultimate.network;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.client.FoxAblazeUltimateClientAccess;

import dev.architectury.networking.NetworkManager;
import dev.architectury.utils.Env;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenRaphaelNamingPayload(
        int containerId,
        String defaultName,
        ResourceLocation skill) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenRaphaelNamingPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    FoxAblazeUltimateMod.MOD_ID, "open_raphael_naming"));

    public static final StreamCodec<FriendlyByteBuf, OpenRaphaelNamingPayload> STREAM_CODEC =
            CustomPacketPayload.codec(OpenRaphaelNamingPayload::encode, OpenRaphaelNamingPayload::new);

    public OpenRaphaelNamingPayload(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readUtf(64), buf.readResourceLocation());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.containerId);
        buf.writeUtf(this.defaultName, 64);
        buf.writeResourceLocation(this.skill);
    }

    public void handle(NetworkManager.PacketContext context) {
        if (context.getEnvironment() == Env.CLIENT) {
            context.queue(() -> FoxAblazeUltimateClientAccess.handleOpenRaphaelNaming(this));
        }
    }

    @Override
    public CustomPacketPayload.Type<OpenRaphaelNamingPayload> type() {
        return TYPE;
    }
}
