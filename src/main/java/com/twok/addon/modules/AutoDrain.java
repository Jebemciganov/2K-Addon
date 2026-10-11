package com.twok.addon.modules;

import com.twok.addon.TwoK;
import com.twok.addon.util.Helper;
import com.twok.addon.util.S;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Picks up nearby water and lava source blocks with an empty bucket. */
public class AutoDrain extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> range = S.dbl(sg, "range", "Max distance to a fluid source.", 4.5, 1, 6);
    private final Setting<Integer> delay = S.in(sg, "delay", "Ticks between pickups.", 3, 0, 20);
    private final Setting<Boolean> water = S.bool(sg, "water", "Pick up water.", true);
    private final Setting<Boolean> lava = S.bool(sg, "lava", "Pick up lava.", true);
    private final Setting<Boolean> rotate = S.bool(sg, "rotate", "Look at the source before picking it up.", true);

    private int timer;

    public AutoDrain() {
        super(TwoK.PVP, "auto-drain", "Automatically scoops up nearby water/lava sources.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (timer > 0) { timer--; return; }

        int bucket = Helper.findHotbar(s -> s.isOf(Items.BUCKET));
        if (bucket == -1) return;

        BlockPos base = mc.player.getBlockPos();
        int r = (int) Math.ceil(range.get());
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            BlockPos p = base.add(dx, dy, dz);
            FluidState fs = mc.world.getFluidState(p);
            if (fs.isEmpty() || !fs.isStill()) continue;
            boolean isWater = fs.isIn(FluidTags.WATER);
            boolean isLava = fs.isIn(FluidTags.LAVA);
            if (!(isWater && water.get()) && !(isLava && lava.get())) continue;

            double d = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(p));
            if (d <= range.get() && d < bestDist) { bestDist = d; best = p; }
        }
        if (best == null) return;

        float[] rot = Helper.rotationTo(Vec3d.ofCenter(best));
        Helper.rotateThen(rot[0], rot[1], rotate.get(), () -> {
            if (!Helper.swap(bucket)) return;
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            Helper.swapBack();
        });
        timer = delay.get();
    }
}
