package com.qirick.workers.item;

import com.qirick.workers.Workers;
import com.qirick.workers.entity.ModEntities;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Workers.MOD_ID);

    public static final RegistryObject<Item> CITIZEN_SPAWN_EGG = ITEMS.register(
            "citizen_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.CITIZEN, 0x8c6239, 0xd7b08a, new Item.Properties()));

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(ModItems::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(CITIZEN_SPAWN_EGG);
        }
    }
}
