package com.twok.addon.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RaycastContext;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Approximate explosion damage. Ignores enchantments, effects and exact toughness,
 * so treat the numbers as estimates (they are close for typical armor).
 */
public final class Dmg {
    private Dmg() {}

    public static double explosion(LivingEntity e, Vec3d center, float power, BlockPos ignoreBlock) {
        double q = power * 2.0;
        double dist = Helper.pos(e).distanceTo(center);
        if (dist > q) return 0;

        double impact = (1.0 - dist / q) * exposure(e, center, ignoreBlock);
        double dmg = (impact * impact + impact) / 2.0 * 7.0 * q + 1.0;

        Difficulty diff = mc.world.getDifficulty();
        if (diff == Difficulty.PEACEFUL) return 0;
        if (diff == Difficulty.EASY) dmg = Math.min(dmg / 2.0 + 1.0, dmg);
        else if (diff == Difficulty.HARD) dmg *= 1.5;

        double armor = e.getArmor();
        double reduction = MathHelper.clamp(armor - dmg / (2.0 + 8.0 / 4.0), armor * 0.2, 20.0);
        dmg *= 1.0 - reduction / 25.0;
        return Math.max(dmg, 0);
    }

    public static double crystal(LivingEntity e, Vec3d center) {
        return explosion(e, center, 6f, null);
    }

    public static double health(LivingEntity e) {
        return e.getHealth() + e.getAbsorptionAmount();
    }

    private static double exposure(LivingEntity e, Vec3d center, BlockPos ignore) {
        double h = e.getHeight();
        Vec3d[] samples = {
            new Vec3d(e.getX(), e.getY() + 0.1, e.getZ()),
            new Vec3d(e.getX(), e.getY() + h * 0.5, e.getZ()),
            new Vec3d(e.getX(), e.getY() + h * 0.9, e.getZ())
        };
        int clear = 0;
        for (Vec3d s : samples) {
            BlockHitResult r = mc.world.raycast(new RaycastContext(center, s,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, e));
            if (r.getType() == HitResult.Type.MISS || (ignore != null && r.getBlockPos().equals(ignore))) clear++;
        }
        return clear / (double) samples.length;
    }
}
