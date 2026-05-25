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

@EmiEntrypoint
public final class FoxAblazeUltimateEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addExclusionArea(BeelzebubStorageScreen.class,
                (screen, consumer) -> emit(screen, consumer));

        registry.addExclusionArea(SpatialStorageScreen.class, (screen, consumer) -> {

            if (!(screen.getMenu() instanceof SpatialStorageMenu menu)) return;
            if (menu.getSkill() != UniqueSkills.GLUTTONY.get()) return;
            emit(screen, consumer);
        });
    }

    private static void emit(AbstractContainerScreen<?> screen, Consumer<Bounds> consumer) {
        PredationFilterOverlay.forEachOverlayBound(
                screen.getGuiLeft(), screen.getGuiTop(), screen.getXSize(),
                (x, y, w, h) -> consumer.accept(new Bounds(x, y, w, h)));
    }
}
