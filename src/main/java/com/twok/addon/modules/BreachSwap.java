package com.twok.addon.modules;

import com.twok.addon.TwoK;
import com.twok.addon.util.Helper;
import com.twok.addon.util.S;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Swaps to a Breach-enchanted mace when you hit an armored target, then swaps back. */
public class BreachSwap extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> requireArmor = S.bool(sg, "require-armor", "Only swap when the target is wearing armor.", true);
    private final Setting<Boolean> requireFalling = S.bool(sg, "require-falling", "Only swap while falling (the mace needs a fall to smash).", true);
    private final Setting<Double> minFall = S.dbl(sg, "min-fall", "Minimum fall distance when require-falling is on.", 1.6, 0, 30);

    private boolean swapped;

    public BreachSwap() {
        super(TwoK.PVP, "breach-swap", "Uses your Breach mace against armored targets.");
    }

    @Override
    public void onDeactivate() {
        if (swapped) Helper.swapBack();
        swapped = false;
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (mc.player == null || swapped) return;
        if (!(event.entity instanceof LivingEntity target)) return;
        if (requireArmor.get() && !Helper.hasArmor(target)) return;
        if (requireFalling.get() && (mc.player.isOnGround() || mc.player.fallDistance < minFall.get())) return;
        if (Helper.enchantLevel(mc.player.getMainHandStack(), "breach") > 0) return;

        int best = -1, bestLevel = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isOf(Items.MACE)) continue;
            int lvl = Helper.enchantLevel(s, "breach");
            if (lvl > bestLevel) { bestLevel = lvl; best = i; }
        }
        if (best != -1 && Helper.swap(best)) swapped = true;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (swapped) {
            Helper.swapBack();
            swapped = false;
        }
    }
}
