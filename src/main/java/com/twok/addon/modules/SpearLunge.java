package com.twok.addon.modules;

import com.twok.addon.TwoK;
import com.twok.addon.util.Helper;
import com.twok.addon.util.S;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Swaps to your best Lunge spear, jabs a nearby player, and swaps back in the same tick. */
public class SpearLunge extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> range = S.dbl(sg, "range", "Max distance to the target.", 3.5, 1, 6);
    private final Setting<Integer> minLunge = S.in(sg, "min-lunge-level", "Only use spears with at least this Lunge level (0 = any spear).", 1, 0, 3);
    private final Setting<Integer> minHunger = S.in(sg, "min-hunger", "Lunge needs enough food; skip below this food level.", 6, 0, 20);
    private final Setting<Double> cooldown = S.dbl(sg, "cooldown", "Attack meter required (spear jabs need a full meter).", 1.0, 0, 1);
    private final Setting<Integer> delay = S.in(sg, "delay", "Ticks between jabs.", 0, 0, 20);
    private final Setting<Boolean> ignoreFriends = S.bool(sg, "ignore-friends", "Never attack friends.", true);
    private final Setting<Boolean> rotate = S.bool(sg, "rotate", "Look at the target before jabbing.", false);

    private int timer;

    public SpearLunge() {
        super(TwoK.PVP, "spear-lunge", "Automatic spear jabs with Lunge.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (timer > 0) { timer--; return; }
        if (mc.player.getHungerManager().getFoodLevel() < minHunger.get()) return;

        PlayerEntity target = Helper.closestPlayer(range.get(), ignoreFriends.get(), false);
        if (target == null || !Helper.cooldownReady(cooldown.get() - 0.001)) return;

        int slot = -1, bestLevel = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!Helper.isSpear(s)) continue;
            int lvl = Helper.enchantLevel(s, "lunge");
            if (lvl >= minLunge.get() && lvl > bestLevel) { bestLevel = lvl; slot = i; }
        }
        if (slot == -1) return;

        final int spear = slot;
        float[] rot = Helper.rotationTo(Helper.center(target));
        Helper.rotateThen(rot[0], rot[1], rotate.get(), () -> {
            if (!Helper.swap(spear)) return;
            Helper.attack(target);
            Helper.swapBack();
        });
        timer = delay.get();
    }
}
