package com.elementalarsenal;

import com.elementalarsenal.item.ArsenalPresets;
import com.elementalarsenal.item.ModItems;
import com.elementalarsenal.skill.ArsenalSkills;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Epic Fight: Elemental Arsenal — аддон к Epic Fight.
 * Шесть стихийных оружий с мувсетами Epic Fight, стихийными эффектами при ударе
 * и собственными врождёнными навыками оружия (клавиша навыка оружия Epic Fight).
 */
@Mod(ElementalArsenal.MODID)
public class ElementalArsenal {
    public static final String MODID = "elemental_arsenal";

    public ElementalArsenal() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        modBus.addListener(ArsenalSkills::onSkillBuild);
        modBus.addListener(ArsenalPresets::onPresetRegistry);
    }
}
