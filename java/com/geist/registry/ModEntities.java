package com.geist.registry;

import com.geist.GeistMod;
import com.geist.entity.SmokeCloudEntity;
import com.geist.entity.SmokePelletEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, GeistMod.MODID);

    public static final RegistryObject<EntityType<SmokePelletEntity>> SMOKE_PELLET = ENTITIES.register("smoke_pellet",
            () -> EntityType.Builder.<SmokePelletEntity>of(SmokePelletEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build("smoke_pellet"));



    public static final RegistryObject<EntityType<com.geist.entity.LightArrowEntity>> LIGHT_ARROW = ENTITIES.register("light_arrow",
            () -> EntityType.Builder.<com.geist.entity.LightArrowEntity>of(com.geist.entity.LightArrowEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(4).updateInterval(20).build("light_arrow"));

    public static final RegistryObject<EntityType<SmokeCloudEntity>> SMOKE_CLOUD = ENTITIES.register("smoke_cloud",
            () -> EntityType.Builder.<SmokeCloudEntity>of(SmokeCloudEntity::new, MobCategory.MISC)
                    .sized(7.0F, 4.0F).clientTrackingRange(8).updateInterval(20).fireImmune().build("smoke_cloud"));
}
