package com.twok.addon.modules;

import com.twok.addon.TwoK;
import com.twok.addon.util.Helper;
import com.twok.addon.util.S;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Look at a respawn anchor: charges it with glowstone, then detonates it. */
public class AnchorMacro extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> charges = S.in(sg, "charges", "Charges to add before exploding (1-4).", 1, 1, 4);
    private final Setting<Integer> delay = S.in(sg, "delay", "Ticks between actions.", 1, 0, 10);
    private final Setting<Integer> explodeSlot = S.in(sg, "explode-slot", "Hotbar slot (1-9) used to detonate. Must not hold glowstone. 0 = first non-glowstone slot.", 0, 0, 9);
    private final Setting<Double> range = S.dbl(sg, "range", "Max distance to the anchor.", 4.5, 1, 6);

    private int timer;

    public AnchorMacro() {
        super(TwoK.PVP, "anchor-macro", "Charges and explodes the respawn anchor you are looking at.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (timer > 0) { timer--; return; }
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)) return;

        BlockState state = mc.world.getBlockState(hit.getBlockPos());
        if (!state.isOf(Blocks.RESPAWN_ANCHOR)) return;
        if (mc.player.getEyePos().distanceTo(hit.getPos()) > range.get()) return;

        int current = state.get(RespawnAnchorBlock.CHARGES);
        if (current < charges.get()) {
            int glow = Helper.findHotbar(s -> s.isOf(Items.GLOWSTONE));
            if (glow == -1) return;
            use(glow, hit);
        } else {
            int slot = detonateSlot();
            if (slot == -1) return;
            use(slot, hit);
        }
        timer = delay.get();
    }

    private int detonateSlot() {
        if (explodeSlot.get() > 0) {
            int i = explodeSlot.get() - 1;
            return mc.player.getInventory().getStack(i).isOf(Items.GLOWSTONE) ? -1 : i;
        }
        for (int i = 0; i < 9; i++) {
            if (!mc.player.getInventory().getStack(i).isOf(Items.GLOWSTONE)) return i;
        }
        return -1;
    }

    private void use(int slot, BlockHitResult hit) {
        if (!Helper.swap(slot)) return;
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        Helper.swapBack();
    }
}
