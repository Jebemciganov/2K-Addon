package com.twok.addon.modules;

import com.twok.addon.TwoK;
import com.twok.addon.util.S;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Filters fake players. Other PVP modules ignore anything this flags. */
public class AntiBot extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> tabList = S.bool(sg, "tab-list", "Players missing from the tab list are bots.", true);
    private final Setting<Boolean> invalidName = S.bool(sg, "invalid-name", "Names that are not valid Minecraft usernames are bots.", true);
    private final Setting<Boolean> duplicates = S.bool(sg, "duplicate-names", "Two players with the same name are both treated as bots.", true);
    private final Setting<Boolean> zeroPing = S.bool(sg, "zero-ping", "Players with 0 ms latency are bots (can false-positive on some servers).", false);
    private final Setting<Integer> minAge = S.in(sg, "min-age", "Ignore players that have existed for fewer than this many ticks.", 0, 0, 100);

    public AntiBot() {
        super(TwoK.PVP, "anti-bot", "Ignores fake players in other PVP modules.");
    }

    public static boolean isBot(PlayerEntity p) {
        AntiBot m = Modules.get().get(AntiBot.class);
        return m != null && m.isActive() && m.check(p);
    }

    private boolean check(PlayerEntity p) {
        if (mc.player == null || mc.world == null || p == mc.player) return false;

        PlayerListEntry entry = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
        if (tabList.get() && entry == null) return true;
        if (zeroPing.get() && entry != null && entry.getLatency() <= 0) return true;

        String name = p.getName().getString();
        if (invalidName.get() && !name.matches("[A-Za-z0-9_]{3,16}")) return true;

        if (duplicates.get()) {
            int count = 0;
            for (PlayerEntity o : mc.world.getPlayers()) {
                if (o.getName().getString().equals(name)) count++;
            }
            if (count > 1) return true;
        }

        return minAge.get() > 0 && p.age < minAge.get();
    }
}
