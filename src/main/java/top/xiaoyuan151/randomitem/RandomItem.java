package top.xiaoyuan151.randomitem;

import net.fabricmc.api.ModInitializer;

public class RandomItem implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        System.out.println("RandomItem Modded");
        System.out.println("Made by XiaoYuan151");
    }
}