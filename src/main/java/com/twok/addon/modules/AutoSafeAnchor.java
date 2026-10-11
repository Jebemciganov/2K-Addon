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
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Places a respawn anchor next to a target, puts a block between you and it,
 * charges it and detonates it, aborting if the blast would hurt you too much.
 */
public class AutoSafeAnchor extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgDamage = settings.createGroup("Damage");

    private final Setting<Double> targetRange = S.dbl(sg, "target-range", "Max distance to a target.", 6, 1, 12);
    private final Setting<Double> placeRange = S.dbl(sg, "place-range", "Max distance to place the anchor.", 4.5, 1, 6);
    private final Setting<Integer> delay = S.in(sg, "delay", "Ticks between actions.", 2, 0, 20);
    private final Setting<Integer> charges = S.in(sg, "charges", "Charges to add before exploding (1-4).", 1, 1, 4);
    private final Setting<Boolean> safeBlock = S.bool(sg, "safe-block", "Place a block between you and the anchor first.", true);
    private final Setting<Boolean> rotate = S.bool(sg, "rotate", "Look at blocks before interacting.", true);
    private final Setting<Boolean> ignoreFriends = S.bool(sg, "ignore-friends", "Never target friends.", true);
    private final Setting<Boolean> autoDisable = S.bool(sg, "auto-disable", "Turn off after one detonation.", false);

    private final Setting<Double> minDamage = S.dbl(sgDamage, "min-damage", "Minimum estimated damage to the target.", 6, 0, 36);
    private final Setting<Double> maxSelf = S.dbl(sgDamage, "max-self-damage", "Abort if the blast would deal more than this to you.", 6, 0, 36);

    private int timer;
    private BlockPos anchorPos;

    public AutoSafeAnchor() {
        super(TwoK.PVP, "auto-safe-anchor", "Automatic anchor bombing with a protective block.");
    }

    @Override
    public void onActivate() { timer = 0; anchorPos = null; }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (timer > 0) { timer--; return; }

        PlayerEntity target = Helper.closestPlayer(targetRange.get(), ignoreFriends.get(), true);
        if (target == null) { anchorPos = null; return; }

        int anchorSlot = Helper.findHotbar(s -> s.isOf(Items.RESPAWN_ANCHOR));
        int glowSlot = Helper.findHotbar(s -> s.isOf(Items.GLOWSTONE));

        if (anchorPos == null) anchorPos = choosePos(target);
        if (anchorPos == null) return;

        BlockState state = mc.world.getBlockState(anchorPos);

        if (!state.isOf(Blocks.RESPAWN_ANCHOR)) {
            if (anchorSlot == -1) return;
            if (safeBlock.get() && placeShield(anchorPos)) { timer = delay.get(); return; }
            if (!Helper.placeBlock(anchorPos, anchorSlot, rotate.get())) anchorPos = null;
            timer = delay.get();
            return;
        }

        Vec3d center = Vec3d.ofCenter(anchorPos);
        double self = Dmg.explosion(mc.player, center, 5f, anchorPos);
        if (self > maxSelf.get() || self >= Dmg.health(mc.player) - 0.5) {
            // Not safe from here; leave this anchor alone and look for another spot next time.
            anchorPos = null;
            timer = delay.get();
            return;
        }

        int current = state.get(RespawnAnchorBlock.CHARGES);
        if (current < charges.get()) {
            if (glowSlot == -1) return;
            interact(glowSlot, anchorPos);
        } else {
            int slot = detonateSlot();
            if (slot == -1) return;
            interact(slot, anchorPos);
            anchorPos = null;
            if (autoDisable.get()) toggle();
        }
        timer = delay.get();
    }

    private BlockPos choosePos(PlayerEntity t) {
        BlockPos tp = t.getBlockPos();
        BlockPos best = null;
        double bestScore = -Double.MAX_VALUE;

        for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 2; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0 && dy < 2) continue; // inside the target
            BlockPos p = tp.add(dx, dy, dz);
            if (p.equals(mc.player.getBlockPos()) || p.equals(mc.player.getBlockPos().up())) continue;
            if (mc.player.getEyePos().distanceTo(Vec3d.ofCenter(p)) > placeRange.get()) continue;
            if (!Helper.canPlace(p)) continue;

            Vec3d c = Vec3d.ofCenter(p);
            double dmg = Dmg.explosion(t, c, 5f, p);
            if (dmg < minDamage.get()) continue;
            double self = Dmg.explosion(mc.player, c, 5f, p);
            double score = dmg - self * 0.5;
            if (score > bestScore) { bestScore = score; best = p; }
        }
        return best;
    }

    /** Places a block one step from our eyes toward the anchor. Returns true if it issued a placement. */
    private boolean placeShield(BlockPos anchor) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d dir = Vec3d.ofCenter(anchor).subtract(eye).normalize();
        BlockPos sp = BlockPos.ofFloored(eye.add(dir.multiply(1.2)));
        if (sp.equals(anchor) || sp.equals(mc.player.getBlockPos()) || sp.equals(mc.player.getBlockPos().up())) return false;
        if (!Helper.canPlace(sp)) return false;

        int slot = Helper.findHotbar(s -> s.isOf(Items.OBSIDIAN));
        if (slot == -1) {
            slot = Helper.findHotbar(s -> s.getItem() instanceof BlockItem bi
                && bi.getBlock() != Blocks.RESPAWN_ANCHOR && bi.getBlock() != Blocks.GLOWSTONE
                && !(bi.getBlock() instanceof FallingBlock));
        }
        return slot != -1 && Helper.placeBlock(sp, slot, rotate.get());
    }

    private int detonateSlot() {
        for (int i = 0; i < 9; i++) {
            if (!mc.player.getInventory().getStack(i).isOf(Items.GLOWSTONE)) return i;
        }
        return -1;
    }

    private void interact(int slot, BlockPos pos) {
        Direction side = Direction.UP;
        Vec3d hitVec = Helper.faceVec(pos, side);
        float[] rot = Helper.rotationTo(hitVec);
        Helper.rotateThen(rot[0], rot[1], rotate.get(), () -> {
            if (!Helper.swap(slot)) return;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hitVec, side, pos, false));
            mc.player.swingHand(Hand.MAIN_HAND);
            Helper.swapBack();
        });
    }
}
