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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class AimAssist extends Module {
    public enum Part { Head, Chest, Feet }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSmooth = settings.createGroup("Smoothing");

    private final Setting<Double> range = S.dbl(sgGeneral, "range", "Max distance to a target.", 5.0, 1, 8);
    private final Setting<Double> fov = S.dbl(sgGeneral, "fov", "Only targets within this field of view (degrees).", 90, 10, 360);
    private final Setting<Part> part = S.en(sgGeneral, "aim-at", "Hitbox region to aim at (AimOptimizer overrides this when enabled).", Part.Chest);
    private final Setting<Boolean> ignoreFriends = S.bool(sgGeneral, "ignore-friends", "Do not aim at friends.", true);
    private final Setting<Boolean> walls = S.bool(sgGeneral, "through-walls", "Aim at targets you cannot see.", false);
    private final Setting<Boolean> weaponOnly = S.bool(sgGeneral, "weapon-only", "Only while holding a weapon.", true);
    private final Setting<Boolean> requireClick = S.bool(sgGeneral, "require-click", "Only while holding attack.", false);

    private final Setting<Double> speed = S.dbl(sgSmooth, "max-speed", "Maximum degrees turned per tick.", 8, 1, 60);
    private final Setting<Double> smoothing = S.dbl(sgSmooth, "smoothing", "Fraction of the remaining angle covered each tick (lower = smoother).", 0.35, 0.05, 1);
    private final Setting<Double> randomness = S.dbl(sgSmooth, "randomness", "Random degrees added to each step.", 0.3, 0, 3);

    private PlayerEntity current;

    public AimAssist() {
        super(TwoK.PVP, "aim-assist", "Smoothly pulls your aim toward nearby players.");
    }

    @Override
    public void onDeactivate() { current = null; }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (weaponOnly.get() && !Helper.isWeapon(mc.player.getMainHandStack())) return;
        if (requireClick.get() && !mc.options.attackKey.isPressed()) return;

        PlayerEntity target = pick();
        if (target == null) return;

        Vec3d point = aimPoint(target);
        float[] rot = Helper.rotationTo(point);
        float dYaw = MathHelper.wrapDegrees(rot[0] - mc.player.getYaw());
        float dPitch = rot[1] - mc.player.getPitch();

        double s = speed.get();
        double k = smoothing.get();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float stepYaw = (float) MathHelper.clamp(dYaw * k + (r.nextDouble() - 0.5) * randomness.get(), -s, s);
        float stepPitch = (float) MathHelper.clamp(dPitch * k + (r.nextDouble() - 0.5) * randomness.get(), -s, s);

        stepYaw = Helper.gcd(stepYaw);
        stepPitch = Helper.gcd(stepPitch);

        mc.player.setYaw(mc.player.getYaw() + stepYaw);
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + stepPitch, -90f, 90f));
    }

    private PlayerEntity pick() {
        // Stay on the current target while it is still valid.
        if (current != null && valid(current)) return current;

        PlayerEntity best = null;
        double bestAngle = Double.MAX_VALUE;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (!valid(p)) continue;
            double a = Helper.angleTo(Helper.center(p));
            if (a < bestAngle) { bestAngle = a; best = p; }
        }
        current = best;
        return best;
    }

    private boolean valid(PlayerEntity p) {
        if (!Helper.validPlayer(p, ignoreFriends.get())) return false;
        if (Helper.eyeDistance(p) > range.get()) return false;
        if (!walls.get() && !mc.player.canSee(p)) return false;
        return Helper.angleTo(Helper.center(p)) <= fov.get() / 2.0;
    }

    private Vec3d aimPoint(PlayerEntity p) {
        Vec3d optimized = AimOptimizer.pointFor(p);
        if (optimized != null) return optimized;
        return switch (part.get()) {
            case Head -> p.getEyePos();
            case Chest -> new Vec3d(p.getX(), p.getY() + p.getHeight() * 0.65, p.getZ());
            case Feet -> new Vec3d(p.getX(), p.getY() + 0.2, p.getZ());
        };
    }
}
