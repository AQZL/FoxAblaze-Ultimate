package com.foxablazeultimate.compat.emi;

import java.util.function.Consumer;

import com.foxablazeultimate.client.screen.BeelzebubStorageScreen;
import com.foxablazeultimate.client.screen.PredationFilterOverlay;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;
import io.github.manasmods.tensura.client.screen.SpatialStorageScreen;
import io.github.manasmods.tensura.menu.SpatialStorageMenu;
import io.github.manasmods.tensura.registry.skill.UniqueSkills;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * EMI 兼容插件：把 {@link PredationFilterOverlay} 当前占用的矩形（按钮 + 展开行面板）
 * 通过 {@link EmiRegistry#addExclusionArea} 告诉 EMI，让 EMI 的右侧侧栏自动避开 overlay。
 *
 * <p><b>软依赖</b>：本类引用 EMI API，{@code build.gradle} 用 {@code compileOnly} 引入。
 * <ul>
 *   <li>EMI 不在时：NeoForge 的 mod 扫描使用 ASM 不会 class-load 本类，
 *       EMI 自身的插件发现也不会运行 → 本类永远保持 dormant，无 {@code NoClassDefFoundError}。</li>
 *   <li>EMI 在时：EMI 通过 {@link EmiEntrypoint} 注解发现并实例化本类，调用
 *       {@link #register(EmiRegistry)} 注册排除区域。EMI 在每帧重算 sidebar 布局时会回调
 *       lambda 拿当前 overlay 矩形，从而自动避开按钮 + 展开行。</li>
 * </ul>
 *
 * <p>覆盖两个目标 Screen：
 * <ul>
 *   <li>{@link BeelzebubStorageScreen}：暴食之王虚数仓库（无条件适用，因为只在该 Screen 才注册）。</li>
 *   <li>{@link SpatialStorageScreen}：Tensura 暴食者虚数仓库；只对 {@code GLUTTONY} 生效，
 *       其它技能复用同一 Screen 时不下发排除区。</li>
 * </ul>
 */
@EmiEntrypoint
public final class FoxAblazeUltimateEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addExclusionArea(BeelzebubStorageScreen.class,
                (screen, consumer) -> emit(screen, consumer));

        registry.addExclusionArea(SpatialStorageScreen.class, (screen, consumer) -> {
            // SpatialStorageScreen 也被其它 Tensura 技能复用，仅暴食者激活 overlay。
            if (!(screen.getMenu() instanceof SpatialStorageMenu menu)) return;
            if (menu.getSkill() != UniqueSkills.GLUTTONY.get()) return;
            emit(screen, consumer);
        });
    }

    /** 把 overlay 当前矩形转成 EMI 的 {@link Bounds} 推给 EMI。 */
    private static void emit(AbstractContainerScreen<?> screen, Consumer<Bounds> consumer) {
        PredationFilterOverlay.forEachOverlayBound(
                screen.getGuiLeft(), screen.getGuiTop(), screen.getXSize(),
                (x, y, w, h) -> consumer.accept(new Bounds(x, y, w, h)));
    }
}
