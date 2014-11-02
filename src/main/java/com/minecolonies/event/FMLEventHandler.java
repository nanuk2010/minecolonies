package com.minecolonies.event;

import com.minecolonies.colony.ColonyManager;
import com.minecolonies.configuration.Configurations;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class FMLEventHandler
{
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event)
    {
        ColonyManager.onServerTick(event);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event)
    {
        ColonyManager.onClientTick(event);
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event)
    {
        ColonyManager.onWorldTick(event);
    }
}