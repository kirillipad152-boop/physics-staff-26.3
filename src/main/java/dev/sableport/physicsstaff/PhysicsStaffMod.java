/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Licensed under the MIT License. See LICENSE.md in the project root.
 */

package dev.sableport.physicsstaff;

import com.mojang.logging.LogUtils;
import dev.sableport.physicsstaff.network.PhysicsStaffPackets;
import dev.sableport.physicsstaff.server.PhysicsStaffServerEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Entry point for the standalone Creative Physics Staff mod running on Sable 26.3.
 */
@Mod(PhysicsStaffMod.MOD_ID)
public final class PhysicsStaffMod {
    public static final String MOD_ID = "physics_staff";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    public static final DeferredItem<PhysicsStaffItem> CREATIVE_PHYSICS_STAFF = ITEMS.registerItem(
            "creative_physics_staff",
            PhysicsStaffItem::new,
            () -> new Item.Properties()
                    .rarity(Rarity.EPIC)
                    .stacksTo(1)
    );

    public PhysicsStaffMod(final ModContainer modContainer, final IEventBus modBus) {
        ITEMS.register(modBus);

        modContainer.registerConfig(ModConfig.Type.SYNCED, PhysicsStaffConfig.SYNCED_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, PhysicsStaffConfig.CLIENT_SPEC);

        modBus.addListener(this::addCreativeTabItems);
        modBus.addListener(PhysicsStaffPackets::register);

        PhysicsStaffServerEvents.register(NeoForge.EVENT_BUS);
    }

    public static Identifier id(final String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private void addCreativeTabItems(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            event.accept(CREATIVE_PHYSICS_STAFF);
        }
    }
}
