package com.foxablazeultimate.network;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.client.ClientCrystalLockState;

import dev.architectury.networking.NetworkManager;
import dev.architectury.utils.Env;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncCrystalLockPayload(boolean raphaelLocked) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncCrystalLockPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    FoxAblazeUltimateMod.MOD_ID, "sync_crystal_lock"));

    public static final StreamCodec<FriendlyByteBuf, SyncCrystalLockPayload> STREAM_CODEC =
            CustomPacketPayload.codec(SyncCrystalLockPayload::encode, SyncCrystalLockPayload::new);

    public SyncCrystalLockPayload(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.raphaelLocked);
    }

    public void handle(NetworkManager.PacketContext context) {
        if (context.getEnvironment() == Env.CLIENT) {
            context.queue(() -> ClientCrystalLockState.setRaphaelCrystalLocked(this.raphaelLocked));
        }
    }

    @Override
    public CustomPacketPayload.Type<SyncCrystalLockPayload> type() {
        return TYPE;
    }
}
