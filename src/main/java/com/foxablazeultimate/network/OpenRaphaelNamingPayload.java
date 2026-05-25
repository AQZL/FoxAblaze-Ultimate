package com.foxablazeultimate.network;

import com.foxablazeultimate.FoxAblazeUltimateMod;
import com.foxablazeultimate.client.FoxAblazeUltimateClientAccess;

import dev.architectury.networking.NetworkManager;
import dev.architectury.utils.Env;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C：服务端通知客户端打开"智慧之王·拉斐尔"命名 GUI。
 *
 * <h3>触发时机</h3>
 * <p>{@link com.foxablazeultimate.ability.skill.raphael.RaphaelSkill#onLearnSkill} 在玩家首次获得
 * 拉斐尔时（instance NBT 中尚无 {@code RaphaelNamed} 标记）发送本 payload；玩家确认或关闭 GUI
 * 之后 instance 上写入 {@code RaphaelNamed=true}，后续遗忘 / 再次 learnSkill 也<b>不会再次弹窗</b>。
 *
 * <h3>字段</h3>
 * <ul>
 *   <li>{@code containerId} —— 与 BeelzebubStorageMenu 同思路；服务端用 {@code player.nextContainerCounter()}
 *       生成，客户端 menu 必须与之一致以承接后续 C2S 重命名请求的对齐校验</li>
 *   <li>{@code defaultName} —— 服务端为客户端预先解析好默认显示名（"智慧之王·拉斐尔"）作为 EditBox 初始值；
 *       这避免客户端需要"再去查 lang"或"反查 skill registry"等复杂解耦</li>
 *   <li>{@code skill} —— 拉斐尔的注册 ID，便于客户端校验本次弹窗对应的是哪一项技能（防止其他模组的 menu 错位）</li>
 * </ul>
 */
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
