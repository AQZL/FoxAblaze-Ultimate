package com.foxablazeultimate.network;

import java.util.ArrayList;
import java.util.List;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.client.FoxAblazeUltimateClientAccess;

import dev.architectury.networking.NetworkManager;
import dev.architectury.utils.Env;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record SyncPredationFilterPayload(int slotCount, List<ItemStack> items) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncPredationFilterPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    FoxAblazeUltimateMod.MOD_ID, "sync_predation_filter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPredationFilterPayload> STREAM_CODEC =
            CustomPacketPayload.codec(SyncPredationFilterPayload::encode, SyncPredationFilterPayload::new);

    public SyncPredationFilterPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(), readItems(buf));
    }

    private static List<ItemStack> readItems(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<ItemStack> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }
        return list;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.slotCount);
        buf.writeVarInt(this.items.size());
        for (ItemStack stack : this.items) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
        }
    }

    public void handle(NetworkManager.PacketContext context) {
        if (context.getEnvironment() == Env.CLIENT) {
            context.queue(() -> FoxAblazeUltimateClientAccess.handleSyncPredationFilter(this));
        }
    }

    @Override
    public CustomPacketPayload.Type<SyncPredationFilterPayload> type() {
        return TYPE;
    }
}
