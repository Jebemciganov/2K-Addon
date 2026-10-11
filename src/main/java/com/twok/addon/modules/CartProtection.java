package com.twok.addon.modules;

import com.twok.addon.TwoK;
import com.twok.addon.util.Helper;
import com.twok.addon.util.S;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Reacts to TNT minecarts near you: equips a totem and breaks slow, unlit carts before they can be used. */
public class CartProtection extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> range = S.dbl(sg, "range", "Carts closer than this count as a threat.", 8, 2, 16);
    private final Setting<Boolean> equipTotem = S.bool(sg, "equip-totem", "Put a totem in your offhand when a cart is near.", true);
    private final Setting<Boolean> breakCarts = S.bool(sg, "break-carts", "Hit slow-moving carts (they drop as items instead of exploding).", true);
    private final Setting<Double> breakRange = S.dbl(sg, "break-range", "Max distance to hit a cart.", 4.0, 1, 6);
    private final Setting<Boolean> warn = S.bool(sg, "chat-warning", "Send a chat message when a cart shows up.", true);

    private boolean warned;

    public CartProtection() {
        super(TwoK.PVP, "cart-protection", "Defends against TNT minecart attacks.");
    }

    @Override
    public void onActivate() { warned = false; }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        Entity nearest = null;
        for (Entity e : mc.world.getEntities()) {
            if (e.getType() != EntityType.TNT_MINECART) continue;
            if (Helper.eyeDistance(e) > range.get()) continue;
            if (nearest == null || Helper.eyeDistance(e) < Helper.eyeDistance(nearest)) nearest = e;
        }

        if (nearest == null) { warned = false; return; }

        if (warn.get() && !warned) {
            info("TNT minecart nearby (%.1f blocks).", Helper.eyeDistance(nearest));
            warned = true;
        }

        if (equipTotem.get()) Helper.fillOffhandWithTotem();

        // Vanilla only detonates a hit cart if it is moving at speed or the hit comes from fire/projectiles.
        if (breakCarts.get() && nearest.getVelocity().horizontalLength() < 0.09
            && Helper.eyeDistance(nearest) <= breakRange.get() && Helper.cooldownReady(0.8)) {
            Helper.attack(nearest);
        }
    }
}
