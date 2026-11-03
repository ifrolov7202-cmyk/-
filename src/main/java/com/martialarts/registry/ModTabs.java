package com.martialarts.registry;

import com.martialarts.MartialArts;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MartialArts.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.martialarts"))
            .icon(() -> new ItemStack(ModItems.KARATE_SCROLL.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.BOXING_SCROLL.get());
                output.accept(ModItems.KARATE_SCROLL.get());
                output.accept(ModItems.MUAY_THAI_SCROLL.get());
                output.accept(ModItems.KUNG_FU_SCROLL.get());
                output.accept(ModItems.TRAINING_BAG.get());
            })
            .build());

    private ModTabs() {}
}
