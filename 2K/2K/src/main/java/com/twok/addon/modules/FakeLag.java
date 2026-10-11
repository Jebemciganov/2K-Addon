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

import java.util.concurrent.ThreadLocalRandom;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Delays your movement packets so others see you with extra latency. */
public class FakeLag extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> minDelay = S.in(sg, "min-delay", "Minimum hold time in ms.", 80, 0, 1000);
    private final Setting<Integer> maxDelay = S.in(sg, "max-delay", "Maximum hold time in ms.", 160, 0, 1000);
    private final Setting<Integer> maxPackets = S.in(sg, "max-packets", "Flush when this many packets are queued.", 40, 5, 200);
    private final Setting<Boolean> onlyNearTarget = S.bool(sg, "only-near-target", "Only lag while a player is within range.", false);
    private final Setting<Double> targetRange = S.dbl(sg, "target-range", "Range for only-near-target.", 10, 2, 30);
    private final Setting<Boolean> releaseOnAttack = S.bool(sg, "release-on-attack", "Flush queued packets when you attack.", true);
    private final Setting<Boolean> releaseOnDamage = S.bool(sg, "release-on-damage", "Flush queued packets when you take damage.", true);

    private final PacketHold hold = new PacketHold();
    private long nextDelay;

    public FakeLag() {
        super(TwoK.PVP, "fake-lag", "Delays outgoing movement packets.");
    }

    @Override
    public void onActivate() { nextDelay = rollDelay(); }

    @Override
    public void onDeactivate() { hold.release(); }

    private long rollDelay() {
        int lo = Math.min(minDelay.get(), maxDelay.get());
        int hi = Math.max(minDelay.get(), maxDelay.get());
        return lo == hi ? lo : ThreadLocalRandom.current().nextInt(lo, hi + 1);
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        hold.onSend(event);
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (releaseOnAttack.get()) hold.flush();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) { hold.release(); return; }

        if (onlyNearTarget.get() && Helper.closestPlayer(targetRange.get(), false, true) == null) {
            hold.release();
            return;
        }
        hold.start();

        if (releaseOnDamage.get() && mc.player.hurtTime > 0) {
            hold.flush();
            return;
        }
        if (hold.age() >= nextDelay || hold.size() >= maxPackets.get()) {
            hold.flush();
            nextDelay = rollDelay();
        }
    }
}
