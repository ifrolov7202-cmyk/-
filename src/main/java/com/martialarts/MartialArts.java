package com.martialarts;

import com.martialarts.data.MartialCapability;
import com.martialarts.network.ModNetwork;
import com.martialarts.registry.ModBlocks;
import com.martialarts.registry.ModItems;
import com.martialarts.registry.ModTabs;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Мод «Боевые искусства» для Minecraft 1.20.1 (Forge).
 */
@Mod(MartialArts.MODID)
public class MartialArts {
    public static final String MODID = "martialarts";

    public MartialArts() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModTabs.TABS.register(modBus);

        modBus.addListener(MartialCapability::register);
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }
}
