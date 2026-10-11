package com.twok.addon.modules;

import com.twok.addon.TwoK;
import com.twok.addon.util.Dmg;
import com.twok.addon.util.Helper;
import com.twok.addon.util.S;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Keeps a totem of undying in your offhand. */
public class AutoTotem extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> delay = S.in(sg, "delay", "Ticks between inventory moves.", 0, 0, 10);
    private final Setting<Double> health = S.dbl(sg, "health-threshold", "Only equip when health is at or below this (0 = always keep a totem equipped).", 0, 0, 36);

    private int timer;

    public AutoTotem() {
        super(TwoK.PVP, "auto-totem", "Keeps a totem in your offhand.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (timer > 0) { timer--; return; }

        if (health.get() > 0 && Dmg.health(mc.player) > health.get()) return;
        if (Helper.fillOffhandWithTotem()) timer = delay.get();
    }
}
