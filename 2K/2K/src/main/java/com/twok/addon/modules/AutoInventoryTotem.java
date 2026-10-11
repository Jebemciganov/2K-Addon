package com.twok.addon.modules;

import com.twok.addon.TwoK;
import com.twok.addon.util.S;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** While your inventory is open, fills the offhand and a hotbar slot with totems. */
public class AutoInventoryTotem extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> offhand = S.bool(sg, "offhand", "Move a totem into the offhand.", true);
    private final Setting<Integer> hotbarSlot = S.in(sg, "hotbar-slot", "Hotbar slot (1-9) to keep a totem in. 0 = off.", 0, 0, 9);
    private final Setting<Integer> delay = S.in(sg, "delay", "Ticks between moves.", 1, 0, 10);
    private final Setting<Boolean> autoClose = S.bool(sg, "auto-close", "Close the inventory once everything is filled.", false);

    private int timer;

    public AutoInventoryTotem() {
        super(TwoK.PVP, "auto-inventory-totem", "Refills totems while your inventory is open.");
    }

    @Override
    public void onActivate() { timer = 0; }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || !(mc.currentScreen instanceof InventoryScreen)) return;
        if (timer > 0) { timer--; return; }

        if (offhand.get() && !mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            FindItemResult r = InvUtils.find(s -> s.isOf(Items.TOTEM_OF_UNDYING), 0, 35);
            if (r.found()) {
                InvUtils.move().from(r.slot()).toOffhand();
                timer = delay.get();
                return;
            }
        }

        int slot = hotbarSlot.get() - 1;
        if (slot >= 0 && !mc.player.getInventory().getStack(slot).isOf(Items.TOTEM_OF_UNDYING)) {
            FindItemResult r = InvUtils.find(s -> s.isOf(Items.TOTEM_OF_UNDYING), 9, 35);
            if (r.found()) {
                InvUtils.move().from(r.slot()).toHotbar(slot);
                timer = delay.get();
                return;
            }
        }

        if (autoClose.get()) mc.player.closeHandledScreen();
    }
}
