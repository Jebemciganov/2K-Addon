package com.twok.addon.util;

import com.twok.addon.modules.AntiBot;
import com.twok.addon.modules.SilentSwap;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

import java.util.function.Predicate;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class Helper {
    private Helper() {}

    private static boolean silentActive;

    // ---------------------------------------------------------------- positions

    public static Vec3d pos(Entity e) {
        return new Vec3d(e.getX(), e.getY(), e.getZ());
    }

    public static Vec3d center(Entity e) {
        return new Vec3d(e.getX(), e.getY() + e.getHeight() / 2.0, e.getZ());
    }

    /** Distance from our eyes to the closest point of the entity's hitbox. */
    public static double eyeDistance(Entity e) {
        Vec3d eye = mc.player.getEyePos();
        Box b = e.getBoundingBox();
        double x = MathHelper.clamp(eye.x, b.minX, b.maxX);
        double y = MathHelper.clamp(eye.y, b.minY, b.maxY);
        double z = MathHelper.clamp(eye.z, b.minZ, b.maxZ);
        return eye.distanceTo(new Vec3d(x, y, z));
    }

    // ---------------------------------------------------------------- rotations

    /** {yaw, pitch} needed to look at a point. */
    public static float[] rotationTo(Vec3d t) {
        Vec3d eye = mc.player.getEyePos();
        double dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
        return new float[]{yaw, MathHelper.clamp(pitch, -90f, 90f)};
    }

    /** Angle in degrees between where we look and a point in the world. */
    public static double angleTo(Vec3d t) {
        Vec3d look = mc.player.getRotationVec(1f);
        Vec3d dir = t.subtract(mc.player.getEyePos()).normalize();
        double dot = MathHelper.clamp(look.dotProduct(dir), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    /** Snap a rotation delta to what a real mouse could produce. */
    public static float gcd(float delta) {
        double f = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        double step = f * f * f * 8.0 * 0.15;
        if (step <= 0) return delta;
        return (float) (Math.round(delta / step) * step);
    }

    /** Rotate (server-side) then run the action, or just run it. */
    public static void rotateThen(float yaw, float pitch, boolean rotate, Runnable action) {
        if (rotate) Rotations.rotate(yaw, pitch, 60, action);
        else action.run();
    }

    // ---------------------------------------------------------------- targeting

    public static boolean validPlayer(PlayerEntity p, boolean ignoreFriends) {
        if (p == null || p == mc.player || !p.isAlive() || p.isSpectator()) return false;
        if (AntiBot.isBot(p)) return false;
        return !(ignoreFriends && Friends.get().isFriend(p));
    }

    public static PlayerEntity closestPlayer(double range, boolean ignoreFriends, boolean throughWalls) {
        PlayerEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (!validPlayer(p, ignoreFriends)) continue;
            double d = eyeDistance(p);
            if (d > range || d >= bestDist) continue;
            if (!throughWalls && !mc.player.canSee(p)) continue;
            best = p;
            bestDist = d;
        }
        return best;
    }

    public static boolean isLivingTarget(Entity e) {
        return e instanceof LivingEntity le && le != mc.player && le.isAlive()
            && !(e instanceof net.minecraft.entity.decoration.ArmorStandEntity);
    }

    public static boolean hasArmor(LivingEntity e) {
        return !e.getEquippedStack(EquipmentSlot.HEAD).isEmpty() || !e.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
            || !e.getEquippedStack(EquipmentSlot.LEGS).isEmpty() || !e.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }

    /** True if the other player is looking roughly toward us. */
    public static boolean facingMe(PlayerEntity p, double maxAngle) {
        Vec3d look = p.getRotationVec(1f);
        Vec3d toMe = mc.player.getEyePos().subtract(p.getEyePos()).normalize();
        double dot = MathHelper.clamp(look.dotProduct(toMe), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot)) <= maxAngle;
    }

    // ---------------------------------------------------------------- items

    public static int findHotbar(Predicate<ItemStack> pred) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && pred.test(s)) return i;
        }
        return -1;
    }

    public static boolean idEndsWith(ItemStack s, String suffix) {
        return !s.isEmpty() && Registries.ITEM.getId(s.getItem()).getPath().endsWith(suffix);
    }

    public static boolean isSpear(ItemStack s) { return idEndsWith(s, "_spear"); }
    public static boolean isAxe(ItemStack s) { return idEndsWith(s, "_axe"); }

    public static boolean isWeapon(ItemStack s) {
        return idEndsWith(s, "_sword") || isAxe(s) || isSpear(s) || s.isOf(Items.MACE) || s.isOf(Items.TRIDENT);
    }

    /** Level of an enchantment by its id path, e.g. "breach", "lunge". */
    public static int enchantLevel(ItemStack stack, String path) {
        if (stack.isEmpty()) return 0;
        for (var entry : stack.getEnchantments().getEnchantmentEntries()) {
            boolean match = entry.getKey().getKey().map(k -> k.getValue().getPath().equals(path)).orElse(false);
            if (match) return entry.getIntValue();
        }
        return 0;
    }

    // ---------------------------------------------------------------- swapping

    /** Select a hotbar slot. Uses the packet-only swap when SilentSwap is on. */
    public static boolean swap(int slot) {
        if (mc.player == null || slot < 0 || slot > 8) return false;
        if (SilentSwap.isPacketMode()) {
            if (slot != mc.player.getInventory().getSelectedSlot()) {
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                silentActive = true;
            }
            return true;
        }
        return InvUtils.swap(slot, true);
    }

    public static void swapBack() {
        if (mc.player == null) return;
        if (silentActive) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().getSelectedSlot()));
            silentActive = false;
        } else {
            InvUtils.swapBack();
        }
    }

    /** Put a totem in the offhand if one is available in the inventory. Returns true if a move was issued. */
    public static boolean fillOffhandWithTotem() {
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return false;
        FindItemResult r = InvUtils.find(Items.TOTEM_OF_UNDYING);
        if (!r.found() || r.isOffhand()) return false;
        InvUtils.move().from(r.slot()).toOffhand();
        return true;
    }

    // ---------------------------------------------------------------- combat

    public static boolean cooldownReady(double min) {
        return mc.player.getAttackCooldownProgress(0.5f) >= min;
    }

    public static void attack(Entity e) {
        mc.interactionManager.attackEntity(mc.player, e);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    // ---------------------------------------------------------------- blocks

    public static boolean canPlace(BlockPos pos) {
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        if (!mc.world.getOtherEntities(null, new Box(pos), e -> !e.isSpectator()).isEmpty()) return false;
        for (Direction d : Direction.values()) {
            if (!mc.world.getBlockState(pos.offset(d)).isReplaceable()) return true;
        }
        return false;
    }

    public static Vec3d faceVec(BlockPos pos, Direction side) {
        return Vec3d.ofCenter(pos).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
    }

    /** Place whatever is in the given hotbar slot at pos, against any neighbouring solid block. */
    public static boolean placeBlock(BlockPos pos, int slot, boolean rotate) {
        if (slot < 0 || !canPlace(pos)) return false;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            if (mc.world.getBlockState(n).isReplaceable()) continue;
            Direction side = d.getOpposite();
            Vec3d hitVec = faceVec(n, side);
            float[] r = rotationTo(hitVec);
            rotateThen(r[0], r[1], rotate, () -> {
                if (!swap(slot)) return;
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hitVec, side, n, false));
                mc.player.swingHand(Hand.MAIN_HAND);
                swapBack();
            });
            return true;
        }
        return false;
    }
}
