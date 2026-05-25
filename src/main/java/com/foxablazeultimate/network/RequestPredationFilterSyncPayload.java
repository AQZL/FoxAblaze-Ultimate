package com.foxablazeultimate.network;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.predation.PredationFilterHelper;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * C2S：客户端打开（或刷新）暴食/暴食之王 GUI 时请求服务端推送当前过滤器内容。
 *
 * <p>无 payload 数据。服务端收到后调用 {@link PredationFilterHelper#sendSync} 回推 {@link SyncPredationFilterPayload}。
 */
public record RequestPredationFilterSyncPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestPredationFilterSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    FoxAblazeUltimateMod.MOD_ID, "request_predation_filter_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestPredationFilterSyncPayload> STREAM_CODEC =
            CustomPacketPayload.codec(RequestPredationFilterSyncPayload::encode, RequestPredationFilterSyncPayload::new);

    public RequestPredationFilterSyncPayload(RegistryFriendlyByteBuf buf) {
        this();
    }

    public void encode(RegistryFriendlyByteBuf buf) {
    }

    public static void handle(RequestPredationFilterSyncPayload payload, NetworkManager.PacketContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
        context.queue(() -> PredationFilterHelper.sendSync(player));
    }

    @Override
    public CustomPacketPayload.Type<RequestPredationFilterSyncPayload> type() {
        return TYPE;
    }
}
