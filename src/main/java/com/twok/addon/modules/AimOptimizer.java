package com.twok.addon.modules;

import com.twok.addon.TwoK;
import com.twok.addon.util.Helper;
import com.twok.addon.util.S;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Picks which point on a target's hitbox other aim modules should aim at.
 * It does not move your camera itself; AimAssist reads from it while it is enabled.
 */
public class AimOptimizer extends Module {
    public enum Mode { Closest, Head, Body }

    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Mode> mode = S.en(sg, "mode", "Closest: point nearest your crosshair. Head/Body: fixed region.", Mode.Closest);
    private final Setting<Double> margin = S.dbl(sg, "margin", "Shrinks the hitbox by this many blocks per side so you aim safely inside it.", 0.12, 0, 0.4);
    private final Setting<Double> jitter = S.dbl(sg, "jitter", "Random offset (blocks) applied to the aim point.", 0.05, 0, 0.3);

    public AimOptimizer() {
        super(TwoK.PVP, "aim-optimizer", "Chooses the best hitbox point for aim modules.");
    }

    /** Returns the optimized aim point, or null if this module is off. */
    public static Vec3d pointFor(Entity e) {
        AimOptimizer m = Modules.get().get(AimOptimizer.class);
        return m != null && m.isActive() ? m.compute(e) : null;
    }

    private Vec3d compute(Entity e) {
        Box b = e.getBoundingBox().contract(margin.get(), 0, margin.get());
        double minY = b.minY + 0.05, maxY = b.maxY - 0.05;
        Vec3d best;

        switch (mode.get()) {
            case Head -> best = new Vec3d((b.minX + b.maxX) / 2, maxY - 0.15, (b.minZ + b.maxZ) / 2);
            case Body -> best = new Vec3d((b.minX + b.maxX) / 2, minY + (maxY - minY) * 0.6, (b.minZ + b.maxZ) / 2);
            default -> {
                best = new Vec3d((b.minX + b.maxX) / 2, (minY + maxY) / 2, (b.minZ + b.maxZ) / 2);
                double bestAngle = Double.MAX_VALUE;
                int nx = 4, ny = 8, nz = 4;
                for (int ix = 0; ix <= nx; ix++) for (int iy = 0; iy <= ny; iy++) for (int iz = 0; iz <= nz; iz++) {
                    Vec3d p = new Vec3d(
                        b.minX + (b.maxX - b.minX) * ix / nx,
                        minY + (maxY - minY) * iy / ny,
                        b.minZ + (b.maxZ - b.minZ) * iz / nz);
                    double a = Helper.angleTo(p);
                    if (a < bestAngle) { bestAngle = a; best = p; }
                }
            }
        }

        double j = jitter.get();
        if (j > 0) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            best = best.add((r.nextDouble() - 0.5) * 2 * j, (r.nextDouble() - 0.5) * 2 * j, (r.nextDouble() - 0.5) * 2 * j);
        }
        return best;
    }
}
