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
import net.minecraft.util.Hand;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Swaps to an axe and hits players who are blocking with a shield, then swaps back. */
public class ShieldBreaker extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> range = S.dbl(sg, "range", "Max distance to the target.", 3.2, 1, 6);
    private final Setting<Double> cooldown = S.dbl(sg, "cooldown", "Attack strength required before swapping.", 0.9, 0, 1);
    private final Setting<Boolean> onlyFacing = S.bool(sg, "only-facing", "Only when the target is facing you (shields only block frontal hits).", true);
    private final Setting<Boolean> ignoreFriends = S.bool(sg, "ignore-friends", "Never hit friends.", true);
    private final Setting<Boolean> rotate = S.bool(sg, "rotate", "Look at the target before hitting.", false);
    private final Setting<Integer> delay = S.in(sg, "delay", "Ticks between attempts.", 2, 0, 20);

    private int timer;

    public ShieldBreaker() {
        super(TwoK.PVP, "shield-breaker", "Axe-swaps to disable enemy shields.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (timer > 0) { timer--; return; }

        PlayerEntity target = null;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (!Helper.validPlayer(p, ignoreFriends.get())) continue;
            if (!p.isBlocking() || Helper.eyeDistance(p) > range.get()) continue;
            if (onlyFacing.get() && !Helper.facingMe(p, 100)) continue;
            if (target == null || Helper.eyeDistance(p) < Helper.eyeDistance(target)) target = p;
        }
        if (target == null) return;
        if (!Helper.cooldownReady(cooldown.get())) return;

        int axe = Helper.findHotbar(Helper::isAxe);
        if (axe == -1) return;

        final PlayerEntity t = target;
        float[] rot = Helper.rotationTo(Helper.center(t));
        Helper.rotateThen(rot[0], rot[1], rotate.get(), () -> {
            if (!Helper.swap(axe)) return;
            mc.interactionManager.attackEntity(mc.player, t);
            mc.player.swingHand(Hand.MAIN_HAND);
            Helper.swapBack();
        });
        timer = delay.get();
    }
}
