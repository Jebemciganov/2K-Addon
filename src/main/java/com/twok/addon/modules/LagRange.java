package com.twok.addon.modules;

import com.twok.addon.TwoK;
import com.twok.addon.util.Helper;
import com.twok.addon.util.PacketHold;
import com.twok.addon.util.S;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Freezes your position on the server while an enemy closes in, then releases it all at once
 * when they are inside the release range.
 */
public class LagRange extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> triggerRange = S.dbl(sg, "trigger-range", "Start holding packets when a player comes within this distance.", 8, 3, 20);
    private final Setting<Double> releaseRange = S.dbl(sg, "release-range", "Release everything when a player is this close.", 3.5, 1, 8);
    private final Setting<Integer> maxHold = S.in(sg, "max-hold", "Never hold longer than this (ms).", 400, 50, 2000);
    private final Setting<Integer> cooldown = S.in(sg, "cooldown", "Wait this long (ms) after a release before holding again.", 500, 0, 3000);
    private final Setting<Boolean> ignoreFriends = S.bool(sg, "ignore-friends", "Ignore friends.", true);
    private final Setting<Boolean> releaseOnDamage = S.bool(sg, "release-on-damage", "Release when you take damage.", true);

    private final PacketHold hold = new PacketHold();
    private long cooldownUntil;

    public LagRange() {
        super(TwoK.PVP, "lag-range", "Holds your packets while enemies approach, then releases them.");
    }

    @Override
    public void onDeactivate() { hold.release(); }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        hold.onSend(event);
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (hold.isHolding()) release();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) { hold.release(); return; }

        PlayerEntity t = Helper.closestPlayer(triggerRange.get(), ignoreFriends.get(), true);
        if (t == null) { if (hold.isHolding()) release(); return; }
        double d = Helper.eyeDistance(t);

        if (hold.isHolding()) {
            boolean hurt = releaseOnDamage.get() && mc.player.hurtTime > 0;
            if (d <= releaseRange.get() || hold.age() >= maxHold.get() || hurt) release();
        } else if (System.currentTimeMillis() >= cooldownUntil && d > releaseRange.get()) {
            hold.start();
        }
    }

    private void release() {
        hold.release();
        cooldownUntil = System.currentTimeMillis() + cooldown.get();
    }
}
