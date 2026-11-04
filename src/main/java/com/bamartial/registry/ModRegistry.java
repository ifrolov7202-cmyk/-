package com.bamartial.registry;

import com.bamartial.BAMartial;
import com.bamartial.skills.StyleKind;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, BAMartial.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BAMartial.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BAMartial.MODID);

    public static final RegistryObject<Block> TRAINING_BAG = BLOCKS.register("training_bag",
            () -> new TrainingBagBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.5f).sound(SoundType.WOOL)));
    public static final RegistryObject<Item> TRAINING_BAG_ITEM = ITEMS.register("training_bag",
            () -> new BlockItem(TRAINING_BAG.get(), new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ba_martialarts"))
            .icon(() -> new ItemStack(TRAINING_BAG_ITEM.get()))
            .displayItems((params, output) -> {
                output.accept(TRAINING_BAG_ITEM.get());
                for (StyleKind kind : StyleKind.values()) {
                    ItemStack book = styleBook(kind);
                    if (!book.isEmpty()) output.accept(book);
                }
            })
            .build());

    private ModRegistry() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    /** Книга навыка Epic Fight с рукопашным стилем. */
    public static ItemStack styleBook(StyleKind kind) {
        Item book = ForgeRegistries.ITEMS.getValue(new ResourceLocation("epicfight", "skillbook"));
        if (book == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(book);
        stack.getOrCreateTag().putString("skill", BAMartial.MODID + ":" + kind.styleId);
        return stack;
    }
}
