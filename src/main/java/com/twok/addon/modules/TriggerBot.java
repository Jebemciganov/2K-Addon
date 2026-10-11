package com.twok.addon.modules;

import com.twok.addon.TwoK;
import com.twok.addon.util.Helper;
import com.twok.addon.util.S;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;

import java.util.concurrent.ThreadLocalRandom;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class TriggerBot extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> range = S.dbl(sg, "range", "Max distance to the target.", 3.0, 1, 6);
    private final Setting<Double> cooldown = S.dbl(sg, "cooldown", "Attack strength required (1.0 = fully charged).", 0.92, 0, 1);
    private final Setting<Integer> delay = S.in(sg, "delay", "Extra ticks to wait after each hit.", 0, 0, 20);
    private final Setting<Integer> randomDelay = S.in(sg, "random-delay", "Adds up to this many random ticks to the delay.", 0, 0, 10);
    private final Setting<Boolean> playersOnly = S.bool(sg, "players-only", "Only attack players (otherwise any living mob).", true);
    private final Setting<Boolean> ignoreFriends = S.bool(sg, "ignore-friends", "Never attack friends.", true);
    private final Setting<Boolean> crits = S.bool(sg, "criticals", "Only attack while falling (critical hits).", false);
    private final Setting<Boolean> weaponOnly = S.bool(sg, "weapon-only", "Only attack while holding a weapon.", false);
    private final Setting<Boolean> pauseUsing = S.bool(sg, "pause-using", "Do not attack while eating or blocking.", true);

    private int timer;

    public TriggerBot() {
        super(TwoK.PVP, "trigger-bot", "Attacks automatically when your crosshair is on a target.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (timer > 0) { timer--; return; }

        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return;
        Entity e = hit.getEntity();
        if (!Helper.isLivingTarget(e)) return;

        if (e instanceof PlayerEntity p) {
            if (!Helper.validPlayer(p, ignoreFriends.get())) return;
        } else if (playersOnly.get()) {
            return;
        }

        if (Helper.eyeDistance(e) > range.get()) return;
        if (weaponOnly.get() && !Helper.isWeapon(mc.player.getMainHandStack())) return;
        if (pauseUsing.get() && mc.player.isUsingItem()) return;
        if (!Helper.cooldownReady(cooldown.get())) return;
        if (crits.get() && (mc.player.fallDistance <= 0 || mc.player.isOnGround() || mc.player.isClimbing())) return;

        Helper.attack(e);
        timer = delay.get() + (randomDelay.get() > 0 ? ThreadLocalRandom.current().nextInt(randomDelay.get() + 1) : 0);
    }
}
