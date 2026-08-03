package com.qirick.workers.entity;

import com.qirick.workers.Workers;
import com.qirick.workers.entity.citizen.CitizenEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Workers.MOD_ID);

    public static final RegistryObject<EntityType<CitizenEntity>> CITIZEN = ENTITY_TYPES.register(
            "citizen",
            () -> EntityType.Builder.<CitizenEntity>of(CitizenEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build(Workers.MOD_ID + ":citizen"));

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(ModEntities::onCreateAttributes);
    }

    private static void onCreateAttributes(EntityAttributeCreationEvent event) {
        event.put(CITIZEN.get(), CitizenEntity.createAttributes().build());
    }
}
