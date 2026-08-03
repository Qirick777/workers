package com.qirick.workers;

import com.mojang.logging.LogUtils;
import com.qirick.workers.entity.ModEntities;
import com.qirick.workers.item.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Workers.MOD_ID)
public class Workers {

    public static final String MOD_ID = "workers";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Workers(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        ModEntities.register(modEventBus);
        ModItems.register(modEventBus);
    }
}
