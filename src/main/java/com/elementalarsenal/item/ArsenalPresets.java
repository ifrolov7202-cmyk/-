package com.elementalarsenal.item;

import com.elementalarsenal.ElementalArsenal;
import com.elementalarsenal.Element;
import com.elementalarsenal.skill.ArsenalSkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import yesman.epicfight.api.forgeevent.WeaponCapabilityPresetRegistryEvent;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.CapabilityItem.Styles;
import yesman.epicfight.world.capabilities.item.Style;
import yesman.epicfight.world.capabilities.item.WeaponCapability;
import yesman.epicfight.world.capabilities.item.WeaponCapabilityPresets;

import java.util.function.Function;

/**
 * Типы оружия Epic Fight: стандартный мувсет + наш врождённый навык.
 * Типы назначаются предметам через data/elemental_arsenal/capabilities/weapons/*.json.
 */
public final class ArsenalPresets {
    private ArsenalPresets() {}

    public static void onPresetRegistry(WeaponCapabilityPresetRegistryEvent event) {
        put(event, Element.INFERNO, WeaponCapabilityPresets.GREATSWORD, Styles.TWO_HAND);
        put(event, Element.FROST, WeaponCapabilityPresets.UCHIGATANA, Styles.TWO_HAND, Styles.SHEATH);
        put(event, Element.STORM, WeaponCapabilityPresets.SPEAR, Styles.ONE_HAND, Styles.TWO_HAND);
        put(event, Element.VOID, WeaponCapabilityPresets.DAGGER, Styles.ONE_HAND, Styles.TWO_HAND);
        put(event, Element.SOUL, WeaponCapabilityPresets.TACHI, Styles.TWO_HAND);
        put(event, Element.GALE, WeaponCapabilityPresets.SWORD, Styles.ONE_HAND, Styles.TWO_HAND);
    }

    private static void put(WeaponCapabilityPresetRegistryEvent event, Element element,
                            Function<Item, CapabilityItem.Builder> base, Style... styles) {
        event.getTypeEntry().put(new ResourceLocation(ElementalArsenal.MODID, element.itemId), item -> {
            CapabilityItem.Builder builder = base.apply(item);
            if (builder instanceof WeaponCapability.Builder weapon) {
                for (Style style : styles) {
                    weapon.innateSkill(style, stack -> ArsenalSkills.get(element));
                }
            }
            return builder;
        });
    }
}
