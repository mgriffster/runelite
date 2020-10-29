package net.runelite.client.plugins.slayerprofittracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("slayerprofittracker")
public interface SlayerProfitConfig extends Config {
    @ConfigItem(
            position = 1,
            keyName = "geprice",
            name = "GE Price",
            description = "Calculate total item value by Grand Exchange price, if disabled then high alchemy value is used."
    )
    default boolean geprice(){ return true;}

    @ConfigItem(
            position = 2,
            keyName = "profitunit",
            name = "Profit Unit",
            description = "Profit numbers can be displayed per kill or per task."
    )
    default KillOrTask profitunit(){ return KillOrTask.TASK;}
}
