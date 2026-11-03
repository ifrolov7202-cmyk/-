package com.martialarts.registry;

import com.martialarts.MartialArts;
import com.martialarts.data.MartialStyle;
import com.martialarts.item.HandWrapsItem;
import com.martialarts.item.StyleScrollItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MartialArts.MODID);

    public static final RegistryObject<Item> BOXING_SCROLL = scroll("boxing_scroll", MartialStyle.BOXING);
    public static final RegistryObject<Item> KARATE_SCROLL = scroll("karate_scroll", MartialStyle.KARATE);
    public static final RegistryObject<Item> MUAY_THAI_SCROLL = scroll("muay_thai_scroll", MartialStyle.MUAY_THAI);
    public static final RegistryObject<Item> KUNG_FU_SCROLL = scroll("kung_fu_scroll", MartialStyle.KUNG_FU);
    public static final RegistryObject<Item> TAEKWONDO_SCROLL = scroll("taekwondo_scroll", MartialStyle.TAEKWONDO);
    public static final RegistryObject<Item> JUDO_SCROLL = scroll("judo_scroll", MartialStyle.JUDO);

    // боевые бинты (оружие-кулаки Epic Fight): тренировочные + по одному на стиль
    public static final RegistryObject<Item> TRAINING_WRAPS = wraps("training_wraps", MartialStyle.NONE);
    public static final RegistryObject<Item> BOXING_WRAPS = wraps("boxing_wraps", MartialStyle.BOXING);
    public static final RegistryObject<Item> KARATE_WRAPS = wraps("karate_wraps", MartialStyle.KARATE);
    public static final RegistryObject<Item> MUAY_THAI_WRAPS = wraps("muay_thai_wraps", MartialStyle.MUAY_THAI);
    public static final RegistryObject<Item> KUNG_FU_WRAPS = wraps("kung_fu_wraps", MartialStyle.KUNG_FU);
    public static final RegistryObject<Item> TAEKWONDO_WRAPS = wraps("taekwondo_wraps", MartialStyle.TAEKWONDO);
    public static final RegistryObject<Item> JUDO_WRAPS = wraps("judo_wraps", MartialStyle.JUDO);

    public static final List<RegistryObject<Item>> WRAPS = List.of(TRAINING_WRAPS, BOXING_WRAPS, KARATE_WRAPS,
            MUAY_THAI_WRAPS, KUNG_FU_WRAPS, TAEKWONDO_WRAPS, JUDO_WRAPS);

    /** Все свитки стилей (для лута и торговца). */
    public static final List<RegistryObject<Item>> SCROLLS = List.of(
            BOXING_SCROLL, KARATE_SCROLL, MUAY_THAI_SCROLL, KUNG_FU_SCROLL, TAEKWONDO_SCROLL, JUDO_SCROLL);

    public static final RegistryObject<Item> TRAINING_BAG = ITEMS.register("training_bag",
            () -> new BlockItem(ModBlocks.TRAINING_BAG.get(), new Item.Properties()));

    private static RegistryObject<Item> wraps(String name, MartialStyle style) {
        return ITEMS.register(name, () -> new HandWrapsItem(style, new Item.Properties().stacksTo(1)));
    }

    private static RegistryObject<Item> scroll(String name, MartialStyle style) {
        return ITEMS.register(name, () -> new StyleScrollItem(style,
                new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    }

    private ModItems() {}
}
