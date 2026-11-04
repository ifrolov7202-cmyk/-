package com.elementalarsenal.item;

import com.elementalarsenal.ElementalArsenal;
import com.elementalarsenal.Element;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ElementalArsenal.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElementalArsenal.MODID);

    public static final Map<Element, RegistryObject<Item>> WEAPONS = new EnumMap<>(Element.class);

    static {
        for (Element element : Element.values()) {
            WEAPONS.put(element, ITEMS.register(element.itemId, () -> new ElementalWeaponItem(element)));
        }
    }

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.elemental_arsenal"))
            .icon(() -> new ItemStack(WEAPONS.get(Element.INFERNO).get()))
            .displayItems((params, output) -> WEAPONS.values().forEach(item -> output.accept(item.get())))
            .build());

    private ModItems() {}
}
