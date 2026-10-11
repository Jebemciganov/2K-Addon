package com.twok.addon;

import com.twok.addon.modules.*;
import com.twok.addon.util.LightIndex;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;

public class TwoK extends MeteorAddon {
    public static final Category PVP = new Category("PVP", Items.END_CRYSTAL.getDefaultStack());
    public static final Category UTILITY = new Category("Utility", Items.COMPASS.getDefaultStack());

    @Override
    public void onInitialize() {
        Modules m = Modules.get();

        // Always collect server light data so SusChunkFinder works on chunks loaded before it was enabled
        try {
            MeteorClient.EVENT_BUS.subscribe(LightIndex.get());
        } catch (Exception e) {
            System.err.println("[2K] Could not subscribe light collector: " + e);
        }

        // Support modules first (other modules read from these)
        m.add(new AntiBot());
        m.add(new SilentSwap());
        m.add(new AimOptimizer());

        // Aim / hit
        m.add(new AimAssist());
        m.add(new TriggerBot());
        m.add(new ShieldBreaker());

        // Totems
        m.add(new AutoTotem());
        m.add(new AutoInventoryTotem());

        // Crystal / anchor / cart
        m.add(new AutoCrystal());
        m.add(new AnchorMacro());
        m.add(new AutoSafeAnchor());
        m.add(new AutoCart());
        m.add(new CartProtection());

        // Mace / spear
        m.add(new AutoMace());
        m.add(new MaceSwap());
        m.add(new BreachSwap());
        m.add(new SpearLunge());

        // Network
        m.add(new FakeLag());
        m.add(new LagRange());

        // Misc
        m.add(new AutoDrain());

        // Utility
        m.add(new SusChunkFinder());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(PVP);
        Modules.registerCategory(UTILITY);
    }

    @Override
    public String getPackage() {
        return "com.twok.addon";
    }
}
