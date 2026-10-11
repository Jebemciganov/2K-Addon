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
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class AutoCrystal extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDamage = settings.createGroup("Damage");

    private final Setting<Double> targetRange = S.dbl(sgGeneral, "target-range", "Players further than this are ignored.", 10, 1, 20);
    private final Setting<Double> placeRange = S.dbl(sgGeneral, "place-range", "Max distance to place a crystal.", 4.5, 1, 6);
    private final Setting<Double> breakRange = S.dbl(sgGeneral, "break-range", "Max distance to break a crystal.", 4.5, 1, 6);
    private final Setting<Integer> placeDelay = S.in(sgGeneral, "place-delay", "Ticks between placements.", 2, 0, 20);
    private final Setting<Integer> breakDelay = S.in(sgGeneral, "break-delay", "Ticks between breaks.", 1, 0, 20);
    private final Setting<Boolean> rotate = S.bool(sgGeneral, "rotate", "Look at the block/crystal before acting.", true);
    private final Setting<Boolean> ignoreFriends = S.bool(sgGeneral, "ignore-friends", "Never target friends.", true);
    private final Setting<Boolean> pauseOnUse = S.bool(sgGeneral, "pause-on-use", "Pause while eating or drinking.", true);

    private final Setting<Double> minDamage = S.dbl(sgDamage, "min-damage", "Minimum damage to the target.", 6, 0, 36);
    private final Setting<Double> maxSelf = S.dbl(sgDamage, "max-self-damage", "Maximum damage to yourself.", 8, 0, 36);
    private final Setting<Double> facePlaceHealth = S.dbl(sgDamage, "face-place-health", "Ignore min-damage when the target is at or below this health.", 8, 0, 36);
    private final Setting<Boolean> antiSuicide = S.bool(sgDamage, "anti-suicide", "Never act if it could kill you.", true);

    private int placeTimer, breakTimer;

    public AutoCrystal() {
        super(TwoK.PVP, "auto-crystal", "Places and breaks end crystals near enemies.");
    }

    @Override
    public void onActivate() { placeTimer = 0; breakTimer = 0; }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (placeTimer > 0) placeTimer--;
        if (breakTimer > 0) breakTimer--;
        if (pauseOnUse.get() && mc.player.isUsingItem()) return;

        List<PlayerEntity> targets = new ArrayList<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (Helper.validPlayer(p, ignoreFriends.get()) && mc.player.distanceTo(p) <= targetRange.get()) targets.add(p);
        }
        if (targets.isEmpty()) return;

        if (breakTimer <= 0 && doBreak(targets)) breakTimer = breakDelay.get();
        if (placeTimer <= 0 && doPlace(targets)) placeTimer = placeDelay.get();
    }

    private double required(PlayerEntity t) {
        return Dmg.health(t) <= facePlaceHealth.get() ? 1.0 : minDamage.get();
    }

    private boolean selfUnsafe(double self) {
        if (self > maxSelf.get()) return true;
        return antiSuicide.get() && self >= Dmg.health(mc.player) - 0.5;
    }

    // ------------------------------------------------------------------ break

    private boolean doBreak(List<PlayerEntity> targets) {
        EndCrystalEntity best = null;
        double bestScore = -Double.MAX_VALUE;

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EndCrystalEntity c) || Helper.eyeDistance(c) > breakRange.get()) continue;
            Vec3d at = Helper.pos(c);
            double self = Dmg.crystal(mc.player, at);
            if (selfUnsafe(self)) continue;

            double bestTarget = 0;
            for (PlayerEntity t : targets) {
                double d = Dmg.crystal(t, at);
                if (d >= required(t)) bestTarget = Math.max(bestTarget, d);
            }
            if (bestTarget <= 0) continue;
            if (bestTarget - self > bestScore) { bestScore = bestTarget - self; best = c; }
        }
        if (best == null) return false;

        final EndCrystalEntity crystal = best;
        float[] rot = Helper.rotationTo(Helper.center(crystal));
        Helper.rotateThen(rot[0], rot[1], rotate.get(), () -> Helper.attack(crystal));
        return true;
    }

    // ------------------------------------------------------------------ place

    private boolean doPlace(List<PlayerEntity> targets) {
        BlockPos center = mc.player.getBlockPos();
        int r = (int) Math.ceil(placeRange.get());
        BlockPos bestPos = null;
        double bestScore = -Double.MAX_VALUE;

        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            BlockPos base = center.add(dx, dy, dz);
            var state = mc.world.getBlockState(base);
            if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) continue;
            if (mc.player.getEyePos().distanceTo(Vec3d.ofCenter(base)) > placeRange.get()) continue;
            if (!mc.world.getBlockState(base.up()).isAir() || !mc.world.getBlockState(base.up(2)).isAir()) continue;

            Box space = new Box(base.getX(), base.getY() + 1, base.getZ(), base.getX() + 1, base.getY() + 3, base.getZ() + 1);
            if (!mc.world.getOtherEntities(null, space, e -> !e.isSpectator()).isEmpty()) continue;

            Vec3d at = new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
            double self = Dmg.crystal(mc.player, at);
            if (selfUnsafe(self)) continue;

            double bestTarget = 0;
            for (PlayerEntity t : targets) {
                double d = Dmg.crystal(t, at);
                if (d >= required(t)) bestTarget = Math.max(bestTarget, d);
            }
            if (bestTarget <= 0) continue;
            if (bestTarget - self > bestScore) { bestScore = bestTarget - self; bestPos = base; }
        }
        if (bestPos == null) return false;
        return placeCrystal(bestPos);
    }

    private boolean placeCrystal(BlockPos base) {
        final Hand hand;
        int found = -1;
        if (mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)) hand = Hand.OFF_HAND;
        else if (mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) hand = Hand.MAIN_HAND;
        else {
            found = Helper.findHotbar(s -> s.isOf(Items.END_CRYSTAL));
            if (found == -1) return false;
            hand = Hand.MAIN_HAND;
        }
        final int slot = found;

        Vec3d hitVec = new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
        float[] rot = Helper.rotationTo(hitVec);
        Helper.rotateThen(rot[0], rot[1], rotate.get(), () -> {
            if (slot != -1 && !Helper.swap(slot)) return;
            mc.interactionManager.interactBlock(mc.player, hand, new BlockHitResult(hitVec, Direction.UP, base, false));
            mc.player.swingHand(hand);
            if (slot != -1) Helper.swapBack();
        });
        return true;
    }
}
