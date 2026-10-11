package com.twok.addon.modules;

import com.twok.addon.TwoK;
import com.twok.addon.util.Helper;
import com.twok.addon.util.S;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Whenever you attack while falling, swaps to a mace for that hit and swaps back right after. */
public class MaceSwap extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> minFall = S.dbl(sg, "min-fall", "Minimum fall distance before swapping.", 1.6, 0, 30);
    private final Setting<Boolean> playersOnly = S.bool(sg, "players-only", "Only swap when hitting players.", false);

    private boolean swapped;

    public MaceSwap() {
        super(TwoK.PVP, "mace-swap", "Swaps to the mace for falling hits.");
    }

    @Override
    public void onDeactivate() {
        if (swapped) Helper.swapBack();
        swapped = false;
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (mc.player == null || swapped) return;
        if (!(event.entity instanceof LivingEntity)) return;
        if (playersOnly.get() && !(event.entity instanceof PlayerEntity)) return;
        if (mc.player.getMainHandStack().isOf(Items.MACE)) return;
        if (mc.player.isOnGround() || mc.player.fallDistance < minFall.get()) return;

        int slot = Helper.findHotbar(s -> s.isOf(Items.MACE));
        if (slot != -1 && Helper.swap(slot)) swapped = true;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (swapped) {
            Helper.swapBack();
            swapped = false;
        }
    }
}
