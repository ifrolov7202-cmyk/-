package com.bamartial;

import com.bamartial.skills.StyleKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetNbtFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import com.bamartial.registry.ModRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

/**
 * Книги рукопашных стилей попадаются в сундуках.
 */
@Mod.EventBusSubscriber(modid = BAMartial.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LootEvents {
    private static final Set<String> CHESTS = Set.of(
            "minecraft:chests/simple_dungeon", "minecraft:chests/abandoned_mineshaft",
            "minecraft:chests/jungle_temple", "minecraft:chests/desert_pyramid",
            "minecraft:chests/stronghold_corridor", "minecraft:chests/stronghold_library",
            "minecraft:chests/village/village_weaponsmith", "minecraft:chests/village/village_temple",
            "minecraft:chests/pillager_outpost", "minecraft:chests/woodland_mansion");

    private LootEvents() {}

    @SubscribeEvent
    public static void onLootLoad(LootTableLoadEvent event) {
        if (!CHESTS.contains(event.getName().toString())) return;
        if (!MartialConfig.get(MartialConfig.BOOKS_IN_LOOT, true)) return;
        Item book = ForgeRegistries.ITEMS.getValue(new ResourceLocation("epicfight", "skillbook"));
        if (book == null) return;
        LootPool.Builder pool = LootPool.lootPool()
                .name("ba_martialarts_books")
                .setRolls(ConstantValue.exactly(1))
                .add(EmptyLootItem.emptyItem().setWeight(70));
        for (StyleKind kind : StyleKind.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("skill", BAMartial.MODID + ":" + kind.styleId);
            pool.add(LootItem.lootTableItem(book).setWeight(5).apply(SetNbtFunction.setTag(tag)));
        }
        event.getTable().addPool(pool.build());
    }

    /** Книги стилей у странствующего торговца (редкие предложения). */
    @SubscribeEvent
    public static void onWandererTrades(WandererTradesEvent event) {
        if (!MartialConfig.get(MartialConfig.BOOKS_AT_TRADER, true)) return;
        for (StyleKind kind : StyleKind.values()) {
            event.getRareTrades().add((trader, random) -> {
                ItemStack book = ModRegistry.styleBook(kind);
                return book.isEmpty() ? null : new MerchantOffer(new ItemStack(Items.EMERALD, 20), new ItemStack(Items.BOOK), book, 1, 15, 0.05f);
            });
        }
    }
}
