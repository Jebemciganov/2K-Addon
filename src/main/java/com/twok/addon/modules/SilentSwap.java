package com.twok.addon.modules;

import com.twok.addon.TwoK;
import com.twok.addon.util.S;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

/**
 * When enabled, every 2K module swaps items by sending only the slot packet, so your
 * client keeps showing the item you were holding. Disable it to use normal visible swaps.
 */
public class SilentSwap extends Module {
    public enum Mode { Packet, Normal }

    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Mode> mode = S.en(sg, "mode", "Packet: server-side only swap. Normal: visible hotbar swap.", Mode.Packet);

    public SilentSwap() {
        super(TwoK.PVP, "silent-swap", "Makes 2K modules swap items server-side only.");
    }

    public static boolean isPacketMode() {
        SilentSwap m = Modules.get().get(SilentSwap.class);
        return m != null && m.isActive() && m.mode.get() == Mode.Packet;
    }
}
