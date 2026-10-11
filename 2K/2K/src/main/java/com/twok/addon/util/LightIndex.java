package com.twok.addon.util;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.LightData;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Remembers where the server reported block light level 5 (the level an amethyst cluster gives off),
 * for the depth band we care about. Fed straight from incoming chunk/light packets, so it works even for
 * blocks the server hides from the client.
 */
public final class LightIndex {
    public static final int MIN_Y = -12;
    public static final int MAX_Y = 80;
    public static final int WATCHED_LEVEL = 5;

    private static final int MIN_SECTION = Math.floorDiv(MIN_Y, 16);
    private static final int MAX_SECTION = Math.floorDiv(MAX_Y, 16);
    private static final long[] NONE = new long[0];
    private static final LightIndex INSTANCE = new LightIndex();

    /** section key -> packed BlockPos of every cell at the watched light level. */
    private final ConcurrentHashMap<Long, long[]> cells = new ConcurrentHashMap<>();
    private final Set<Long> dirty = ConcurrentHashMap.newKeySet();

    private LightIndex() {}

    public static LightIndex get() { return INSTANCE; }

    public void clear() {
        cells.clear();
        dirty.clear();
    }

    /** Chunks whose light data changed since the last call. */
    public List<Long> drainDirty() {
        List<Long> out = new ArrayList<>(dirty);
        out.forEach(dirty::remove);
        return out;
    }

    /** Packed BlockPos values (BlockPos.fromLong) of watched-level cells in a chunk. */
    public long[] cellsIn(int chunkX, int chunkZ) {
        long[][] parts = new long[MAX_SECTION - MIN_SECTION + 1][];
        int total = 0;
        for (int sy = MIN_SECTION; sy <= MAX_SECTION; sy++) {
            long[] c = cells.get(ChunkSectionPos.asLong(chunkX, sy, chunkZ));
            parts[sy - MIN_SECTION] = c;
            if (c != null) total += c.length;
        }
        long[] out = new long[total];
        int at = 0;
        for (long[] p : parts) {
            if (p == null) continue;
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }

    // ------------------------------------------------------------------ packets

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (event.packet instanceof ChunkDataS2CPacket p) {
            ingest(p.getChunkX(), p.getChunkZ(), p.getLightData());
        } else if (event.packet instanceof LightUpdateS2CPacket p) {
            ingest(p.getChunkX(), p.getChunkZ(), p.getData());
        }
    }

    @EventHandler
    private void onLeft(GameLeftEvent event) {
        clear();
    }

    private void ingest(int cx, int cz, LightData data) {
        ClientWorld world = mc.world;
        if (world == null || data == null) return;

        // Light arrays start one section below the world's lowest block section.
        int firstLightSection = world.getBottomSectionCoord() - 1;
        List<byte[]> arrays = data.getBlockNibbles();
        BitSet present = data.getInitedBlock();
        BitSet empty = data.getUninitedBlock();
        boolean changed = false;

        int arrayIndex = 0;
        for (int bit = present.nextSetBit(0); bit >= 0; bit = present.nextSetBit(bit + 1)) {
            byte[] nibbles = arrayIndex < arrays.size() ? arrays.get(arrayIndex) : null;
            arrayIndex++;
            int sy = firstLightSection + bit;
            if (sy < MIN_SECTION || sy > MAX_SECTION || nibbles == null || nibbles.length != 2048) continue;

            long key = ChunkSectionPos.asLong(cx, sy, cz);
            long[] found = collect(nibbles, cx, sy, cz);
            if (found.length == 0) changed |= cells.remove(key) != null;
            else { cells.put(key, found); changed = true; }
        }

        for (int bit = empty.nextSetBit(0); bit >= 0; bit = empty.nextSetBit(bit + 1)) {
            int sy = firstLightSection + bit;
            if (sy < MIN_SECTION || sy > MAX_SECTION) continue;
            changed |= cells.remove(ChunkSectionPos.asLong(cx, sy, cz)) != null;
        }

        if (changed) dirty.add(ChunkPos.toLong(cx, cz));
    }

    private static long[] collect(byte[] nibbles, int cx, int sy, int cz) {
        long[] out = new long[16];
        int n = 0;
        for (int i = 0; i < 4096; i++) {
            int b = nibbles[i >> 1] & 0xFF;
            int level = (i & 1) == 0 ? (b & 15) : (b >>> 4);
            if (level != WATCHED_LEVEL) continue;

            int y = (sy << 4) + (i >> 8);
            if (y < MIN_Y || y > MAX_Y) continue;

            if (n == out.length) out = Arrays.copyOf(out, n * 2);
            out[n++] = BlockPos.asLong((cx << 4) + (i & 15), y, (cz << 4) + ((i >> 4) & 15));
        }
        return n == 0 ? NONE : Arrays.copyOf(out, n);
    }
}
