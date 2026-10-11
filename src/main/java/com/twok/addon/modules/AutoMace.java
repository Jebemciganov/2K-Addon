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
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Smashes players with the mace while you fall onto them, optionally launching yourself with a wind charge first. */
public class AutoMace extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgBoost = settings.createGroup("Wind Charge");

    private final Setting<Double> range = S.dbl(sg, "range", "Max attack distance.", 3.3, 1, 6);
    private final Setting<Double> minFall = S.dbl(sg, "min-fall", "Minimum fall distance before swinging (smash needs > 1.5).", 1.6, 0, 30);
    private final Setting<Boolean> ignoreFriends = S.bool(sg, "ignore-friends", "Never attack friends.", true);
    private final Setting<Boolean> rotate = S.bool(sg, "rotate", "Look at the target before attacking.", false);

    private final Setting<Boolean> windBoost = S.bool(sgBoost, "wind-charge-boost", "Throw a wind charge at your feet to launch yourself when a target is near.", false);
    private final Setting<Double> boostRange = S.dbl(sgBoost, "boost-range", "Only boost when a target is within this distance.", 6, 2, 12);
    private final Setting<Integer> boostCooldown = S.in(sgBoost, "boost-cooldown", "Ticks between boosts.", 30, 5, 100);

    private int boostTimer;

    public AutoMace() {
        super(TwoK.PVP, "auto-mace", "Automatic mace smash attacks.");
    }

    @Override
    public void onActivate() { boostTimer = 0; }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (boostTimer > 0) boostTimer--;

        int mace = Helper.findHotbar(s -> s.isOf(Items.MACE));
        if (mace == -1) return;

        PlayerEntity target = Helper.closestPlayer(Math.max(range.get(), boostRange.get()), ignoreFriends.get(), true);
        if (target == null) return;
        double dist = Helper.eyeDistance(target);

        if (windBoost.get() && mc.player.isOnGround() && boostTimer <= 0 && dist <= boostRange.get()) {
            int wind = Helper.findHotbar(s -> s.isOf(Items.WIND_CHARGE));
            if (wind != -1) {
                boostTimer = boostCooldown.get();
                Helper.rotateThen(mc.player.getYaw(), 90f, true, () -> {
                    if (!Helper.swap(wind)) return;
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    Helper.swapBack();
                });
            }
            return;
        }

        if (mc.player.isOnGround() || mc.player.fallDistance < minFall.get() || mc.player.getVelocity().y >= 0) return;
        if (dist > range.get() || !Helper.cooldownReady(0.9)) return;

        float[] rot = Helper.rotationTo(Helper.center(target));
        Helper.rotateThen(rot[0], rot[1], rotate.get(), () -> {
            if (!Helper.swap(mace)) return;
            Helper.attack(target);
            Helper.swapBack();
        });
    }
}
