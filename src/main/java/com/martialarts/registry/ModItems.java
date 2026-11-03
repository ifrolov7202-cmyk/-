package com.martialarts.registry;

import com.martialarts.MartialArts;
import com.martialarts.data.MartialStyle;
import com.martialarts.item.StyleScrollItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MartialArts.MODID);

    public static final RegistryObject<Item> BOXING_SCROLL = scroll("boxing_scroll", MartialStyle.BOXING);
    public static final RegistryObject<Item> KARATE_SCROLL = scroll("karate_scroll", MartialStyle.KARATE);
    public static final RegistryObject<Item> MUAY_THAI_SCROLL = scroll("muay_thai_scroll", MartialStyle.MUAY_THAI);
    public static final RegistryObject<Item> KUNG_FU_SCROLL = scroll("kung_fu_scroll", MartialStyle.KUNG_FU);

    public static final RegistryObject<Item> TRAINING_BAG = ITEMS.register("training_bag",
            () -> new BlockItem(ModBlocks.TRAINING_BAG.get(), new Item.Properties()));

    private static RegistryObject<Item> scroll(String name, MartialStyle style) {
        return ITEMS.register(name, () -> new StyleScrollItem(style,
                new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    }

    private ModItems() {}
}
