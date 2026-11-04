package com.bamartial;

import com.bamartial.network.ModNetwork;
import com.bamartial.registry.ModRegistry;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import com.bamartial.skills.MartialSkills;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Battle Arts: Martial Arts — дополнение к Epic Fight - Battle Arts.
 * Добавляет рукопашные стили боя (Battle Style) с боевыми (Combat Art) и взрывными (Burst Art) искусствами.
 * Всё управление — клавиши Epic Fight и Battle Arts.
 */
@Mod(BAMartial.MODID)
public class BAMartial {
    public static final String MODID = "ba_martialarts";

    public BAMartial() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistry.register(modBus);
        modBus.addListener(MartialSkills::onSkillBuild);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MartialConfig.SPEC);
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }
}
