package com.efstamina;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

/**
 * Бесконечная выносливость Epic Fight: на сервере каждый тик выносливость игрока = максимум.
 */
@Mod(InfiniteStamina.MODID)
public class InfiniteStamina {
    public static final String MODID = "ef_infinite_stamina";

    public InfiniteStamina() {
        MinecraftForge.EVENT_BUS.addListener(InfiniteStamina::onPlayerTick);
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side != LogicalSide.SERVER) return;
        PlayerPatch<?> patch = EpicFightCapabilities.getEntityPatch(event.player, PlayerPatch.class);
        if (patch == null) return;
        float max = patch.getMaxStamina();
        if (patch.getStamina() < max) {
            patch.setStamina(max);
        }
    }
}
