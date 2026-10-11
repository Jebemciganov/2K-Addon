package com.twok.addon.modules;

import com.twok.addon.TwoK;
import com.twok.addon.util.Helper;
import com.twok.addon.util.S;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Places a rail near a target, drops a TNT minecart on it and lights it. */
public class AutoCart extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> targetRange = S.dbl(sg, "target-range", "Max distance to a target.", 6, 1, 12);
    private final Setting<Double> placeRange = S.dbl(sg, "place-range", "Max distance to place the rail.", 4.5, 1, 6);
    private final Setting<Double> safeDistance = S.dbl(sg, "safe-distance", "Never place the rail closer than this to you.", 4.0, 0, 8);
    private final Setting<Integer> delay = S.in(sg, "delay", "Ticks between actions.", 2, 0, 20);
    private final Setting<Integer> cooldown = S.in(sg, "cooldown", "Ticks to wait after lighting a cart.", 40, 0, 200);
    private final Setting<Boolean> rotate = S.bool(sg, "rotate", "Look at things before interacting.", true);
    private final Setting<Boolean> ignoreFriends = S.bool(sg, "ignore-friends", "Never target friends.", true);
    private final Setting<Boolean> autoDisable = S.bool(sg, "auto-disable", "Turn off after lighting one cart.", false);

    private int timer;
    private BlockPos railPos;

    public AutoCart() {
        super(TwoK.PVP, "auto-cart", "Rail + TNT minecart + flint, automatically.");
    }

    @Override
    public void onActivate() { timer = 0; railPos = null; }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (timer > 0) { timer--; return; }

        PlayerEntity target = Helper.closestPlayer(targetRange.get(), ignoreFriends.get(), true);
        if (target == null) { railPos = null; return; }

        int rail = Helper.findHotbar(s -> s.isOf(Items.RAIL));
        int cart = Helper.findHotbar(s -> s.isOf(Items.TNT_MINECART));
        int ignite = Helper.findHotbar(s -> s.isOf(Items.FLINT_AND_STEEL));
        if (ignite == -1) ignite = Helper.findHotbar(s -> s.isOf(Items.FIRE_CHARGE));
        if (rail == -1 || cart == -1 || ignite == -1) return;

        if (railPos == null) railPos = chooseRail(target);
        if (railPos == null) return;

        if (!(mc.world.getBlockState(railPos).getBlock() instanceof AbstractRailBlock)) {
            if (!Helper.placeBlock(railPos, rail, rotate.get())) railPos = null;
            timer = delay.get();
            return;
        }

        Entity existing = findCart(railPos);
        if (existing == null) {
            useOnBlock(cart, railPos);
            timer = delay.get();
            return;
        }

        // Light it.
        final int igniteSlot = ignite;
        final Entity c = existing;
        float[] rot = Helper.rotationTo(Helper.center(c));
        Helper.rotateThen(rot[0], rot[1], rotate.get(), () -> {
            if (!Helper.swap(igniteSlot)) return;
            mc.interactionManager.interactEntity(mc.player, c, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            Helper.swapBack();
        });
        railPos = null;
        timer = cooldown.get();
        if (autoDisable.get()) toggle();
    }

    private Entity findCart(BlockPos pos) {
        Vec3d c = Vec3d.ofCenter(pos);
        for (Entity e : mc.world.getEntities()) {
            if (e.getType() == EntityType.TNT_MINECART && Helper.pos(e).distanceTo(c) < 1.5) return e;
        }
        return null;
    }

    private BlockPos chooseRail(PlayerEntity t) {
        BlockPos tp = t.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos p = tp.add(dx, 0, dz);
            if (!Helper.canPlace(p)) continue;
            if (!mc.world.getBlockState(p.down()).isSolidBlock(mc.world, p.down())) continue;
            Vec3d c = Vec3d.ofCenter(p);
            if (mc.player.getEyePos().distanceTo(c) > placeRange.get()) continue;
            if (Helper.pos(mc.player).distanceTo(c) < safeDistance.get()) continue;
            double d = Helper.pos(t).distanceTo(c);
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    private void useOnBlock(int slot, BlockPos pos) {
        Vec3d hitVec = Helper.faceVec(pos, Direction.UP);
        float[] rot = Helper.rotationTo(hitVec);
        Helper.rotateThen(rot[0], rot[1], rotate.get(), () -> {
            if (!Helper.swap(slot)) return;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hitVec, Direction.UP, pos, false));
            mc.player.swingHand(Hand.MAIN_HAND);
            Helper.swapBack();
        });
    }
}
