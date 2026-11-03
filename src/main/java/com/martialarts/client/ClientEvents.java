package com.martialarts.client;

import com.martialarts.MartialArts;
import com.martialarts.network.ActionPacket;
import com.martialarts.network.ModNetwork;
import com.martialarts.skill.Skill;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class ClientEvents {
    private ClientEvents() {}

    @Mod.EventBusSubscriber(modid = MartialArts.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            for (KeyMapping key : KeyBindings.SKILL_KEYS) event.register(key);
            event.register(KeyBindings.CYCLE_STYLE);
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "martial_hud", HudOverlay.HUD);
        }
    }

    @Mod.EventBusSubscriber(modid = MartialArts.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeBusEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;
            for (Skill skill : Skill.values()) {
                KeyMapping key = KeyBindings.forSkill(skill);
                while (key.consumeClick()) {
                    ModNetwork.sendToServer(new ActionPacket(skill.ordinal()));
                }
            }
            while (KeyBindings.CYCLE_STYLE.consumeClick()) {
                ModNetwork.sendToServer(new ActionPacket(ActionPacket.CYCLE_STYLE));
            }
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientData.received = false;
        }
    }
}
