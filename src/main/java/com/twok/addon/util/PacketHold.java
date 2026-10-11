package com.twok.addon.util;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Holds outgoing movement packets and releases them later, in order. */
public class PacketHold {
    /** Shared so one module's release is not re-captured by another. */
    private static boolean releasing;

    private final List<Packet<?>> queue = new ArrayList<>();
    private long since = System.currentTimeMillis();
    private boolean holding;

    public boolean isHolding() { return holding; }
    public int size() { return queue.size(); }
    public long age() { return System.currentTimeMillis() - since; }

    public synchronized void start() {
        if (!holding) since = System.currentTimeMillis();
        holding = true;
    }

    public synchronized void onSend(PacketEvent.Send event) {
        if (!holding || releasing) return;
        if (event.packet instanceof PlayerMoveC2SPacket) {
            event.cancel();
            queue.add(event.packet);
        }
    }

    /** Send everything queued but keep holding. */
    public synchronized void flush() {
        if (mc.getNetworkHandler() != null && !queue.isEmpty()) {
            releasing = true;
            try {
                for (Packet<?> p : queue) mc.getNetworkHandler().sendPacket(p);
            } finally {
                releasing = false;
            }
        }
        queue.clear();
        since = System.currentTimeMillis();
    }

    /** Send everything queued and stop holding. */
    public synchronized void release() {
        holding = false;
        flush();
    }
}
