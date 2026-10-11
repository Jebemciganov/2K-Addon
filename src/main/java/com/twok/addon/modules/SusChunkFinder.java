package com.twok.addon.modules;

import com.twok.addon.TwoK;
import com.twok.addon.util.LightIndex;
import com.twok.addon.util.S;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Finds chunks that look like they have been kept loaded by a player: plants that have grown far past what
 * world generation produces, and hidden amethyst revealed by the server's light data.
 * Neighbouring flagged chunks are merged into zones and highlighted.
 */
public class SusChunkFinder extends Module {
    private enum Signal {
        AMETHYST(6, 16), KELP(2, 6), BAMBOO(2, 6), BERRIES(2, 5), VINES(2, 6), DRIPSTONE(2, 5);

        final double weight;
        final int cap;

        Signal(double weight, int cap) { this.weight = weight; this.cap = cap; }
    }

    public enum Notify { Off, Chat }

    private record Zone(Set<Long> chunks, double x, double z, double total) {}

    private static final class ChunkScore {
        final EnumMap<Signal, Integer> hits = new EnumMap<>(Signal.class);
        final List<BlockPos> geodeCells = new ArrayList<>();
        double score, weightSum, weightedX, weightedZ;

        void hit(Signal s, BlockPos p) {
            hits.merge(s, 1, Integer::sum);
            weightSum += s.weight;
            weightedX += (p.getX() + 0.5) * s.weight;
            weightedZ += (p.getZ() + 0.5) * s.weight;
        }

        void finish() {
            double t = 0;
            for (var e : hits.entrySet()) t += e.getKey().weight * Math.min(e.getValue(), e.getKey().cap);
            score = t;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSignals = settings.createGroup("Signals");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> sensitivity = S.in(sgGeneral, "sensitivity", "How easily chunks are flagged. A chunk needs sensitivity x 5 points (lower = more results).", 3, 1, 10);
    private final Setting<Integer> scanSpeed = S.in(sgGeneral, "scan-speed", "Max chunks scanned per tick.", 4, 1, 32);
    private final Setting<Integer> mergeRadius = S.in(sgGeneral, "merge-radius", "Flagged chunks within this many chunks of each other become one zone.", 3, 1, 8);
    private final Setting<Notify> notify = S.en(sgGeneral, "notifications", "How to announce a newly found zone.", Notify.Chat);

    private final Setting<Boolean> amethyst = S.bool(sgSignals, "hidden-amethyst", "Light-level-5 cells next to amethyst (reveals geodes the server hides).", true);
    private final Setting<Boolean> kelp = S.bool(sgSignals, "kelp", "Fully grown / very tall kelp.", true);
    private final Setting<Integer> kelpHeight = S.in(sgSignals, "kelp-height", "Minimum kelp column height.", 14, 5, 25);
    private final Setting<Boolean> bamboo = S.bool(sgSignals, "bamboo", "Tall bamboo.", true);
    private final Setting<Integer> bambooHeight = S.in(sgSignals, "bamboo-height", "Minimum bamboo height.", 12, 5, 16);
    private final Setting<Boolean> berries = S.bool(sgSignals, "sweet-berries", "Fully grown sweet berry bushes.", true);
    private final Setting<Boolean> vines = S.bool(sgSignals, "vines", "Long hanging vines.", true);
    private final Setting<Integer> vineLength = S.in(sgSignals, "vine-length", "Minimum vine length.", 7, 3, 20);
    private final Setting<Boolean> dripstone = S.bool(sgSignals, "dripstone", "Long dripstone stalactites.", true);
    private final Setting<Integer> dripstoneLength = S.in(sgSignals, "dripstone-length", "Minimum stalactite length.", 5, 3, 12);

    private final Setting<Boolean> render = S.bool(sgRender, "render", "Outline flagged chunks.", true);
    private final Setting<ShapeMode> shapeMode = S.en(sgRender, "shape-mode", "How the outline is drawn.", ShapeMode.Both);
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color").description("Fill color.").defaultValue(new SettingColor(255, 0, 200, 40)).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").description("Outline color.").defaultValue(new SettingColor(255, 0, 200, 255)).build());

    private final Map<Long, ChunkScore> scores = new HashMap<>();
    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Set<Long> announced = new HashSet<>();
    private volatile List<Zone> zones = List.of();
    private ChunkPos lastCenter;
    private int ticks;

    public SusChunkFinder() {
        super(TwoK.UTILITY, "sus-chunk-finder", "Highlights chunks that look player-loaded (overgrown plants, hidden geodes).");
    }

    @Override
    public void onActivate() { reset(); }

    @EventHandler
    private void onLeft(GameLeftEvent event) { reset(); }

    private void reset() {
        scores.clear();
        queue.clear();
        announced.clear();
        zones = List.of();
        lastCenter = null;
        ticks = 0;
    }

    // ------------------------------------------------------------------ scanning

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        ticks++;

        // Chunks that got new light data need a fresh look.
        for (long key : LightIndex.get().drainDirty()) {
            if (scores.remove(key) != null) queue.addFirst(key);
        }
        refillQueue();

        long deadline = System.nanoTime() + 2_000_000L;
        int done = 0;
        while (done < scanSpeed.get() && !queue.isEmpty() && System.nanoTime() < deadline) {
            long key = queue.pollFirst();
            if (scores.containsKey(key)) continue;
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
            if (chunk == null) continue;
            scores.put(key, scan(chunk));
            done++;
        }

        if (ticks % 10 == 0) rebuild();
    }

    private void refillQueue() {
        ChunkPos center = mc.player.getChunkPos();
        boolean moved = lastCenter == null
            || Math.max(Math.abs(center.x - lastCenter.x), Math.abs(center.z - lastCenter.z)) >= 3;
        if (!moved && !(queue.isEmpty() && ticks % 20 == 0)) return;

        lastCenter = center;
        int radius = Math.min(mc.options.getViewDistance().getValue() + 1, 16);
        List<Long> order = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            long key = ChunkPos.toLong(center.x + dx, center.z + dz);
            if (!scores.containsKey(key)) order.add(key);
        }
        order.sort(Comparator.comparingDouble(k -> Math.hypot(ChunkPos.getPackedX(k) - center.x, ChunkPos.getPackedZ(k) - center.z)));
        queue.clear();
        queue.addAll(order);
    }

    private ChunkScore scan(WorldChunk chunk) {
        ChunkScore result = new ChunkScore();
        if (amethyst.get()) scanLight(chunk, result);
        scanPlants(chunk, result);
        result.finish();
        return result;
    }

    private void scanLight(WorldChunk chunk, ChunkScore result) {
        for (long packed : LightIndex.get().cellsIn(chunk.getPos().x, chunk.getPos().z)) {
            BlockPos pos = BlockPos.fromLong(packed);
            BlockState state = mc.world.getBlockState(pos);
            if ((state.isAir() || state.isOf(Blocks.AMETHYST_CLUSTER)) && touchesAmethyst(pos)) {
                result.geodeCells.add(pos);
            }
        }
    }

    private static boolean touchesAmethyst(BlockPos center) {
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dy == 0 && dz == 0) continue;
            p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
            BlockState s = mc.world.getBlockState(p);
            if (s.isOf(Blocks.BUDDING_AMETHYST) || s.isOf(Blocks.AMETHYST_BLOCK)) return true;
        }
        return false;
    }

    private static boolean isPlant(BlockState s) {
        Block b = s.getBlock();
        return b == Blocks.KELP || b == Blocks.KELP_PLANT || b == Blocks.BAMBOO || b == Blocks.SWEET_BERRY_BUSH
            || b == Blocks.VINE || b == Blocks.POINTED_DRIPSTONE;
    }

    private void scanPlants(WorldChunk chunk, ChunkScore result) {
        ChunkSection[] sections = chunk.getSectionArray();
        int bottom = chunk.getBottomSectionCoord();
        int lo = Math.floorDiv(LightIndex.MIN_Y, 16), hi = Math.floorDiv(LightIndex.MAX_Y, 16);
        int baseX = chunk.getPos().getStartX(), baseZ = chunk.getPos().getStartZ();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int i = 0; i < sections.length; i++) {
            int sy = bottom + i;
            if (sy < lo || sy > hi) continue;
            ChunkSection section = sections[i];
            if (section.isEmpty() || !section.getBlockStateContainer().hasAny(SusChunkFinder::isPlant)) continue;

            for (int y = 0; y < 16; y++) {
                int worldY = (sy << 4) + y;
                if (worldY < LightIndex.MIN_Y || worldY > LightIndex.MAX_Y) continue;
                for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    if (!isPlant(state)) continue;
                    pos.set(baseX + x, worldY, baseZ + z);
                    inspect(state, pos, result);
                }
            }
        }
    }

    private void inspect(BlockState state, BlockPos pos, ChunkScore result) {
        Block block = state.getBlock();

        if ((block == Blocks.KELP || block == Blocks.KELP_PLANT) && kelp.get()) {
            if (isKelp(mc.world.getBlockState(pos.up()))) return; // only judge the top of a column
            int height = run(pos, Direction.DOWN, Blocks.KELP, Blocks.KELP_PLANT);
            boolean fullyGrown = block == Blocks.KELP && state.contains(Properties.AGE_25) && state.get(Properties.AGE_25) == 25;
            if (height >= kelpHeight.get() || (fullyGrown && height >= 8)) result.hit(Signal.KELP, pos.toImmutable());
        } else if (block == Blocks.BAMBOO && bamboo.get()) {
            if (mc.world.getBlockState(pos.down()).isOf(Blocks.BAMBOO)) return; // judge from the base
            if (run(pos, Direction.UP, Blocks.BAMBOO) >= bambooHeight.get()) result.hit(Signal.BAMBOO, pos.toImmutable());
        } else if (block == Blocks.SWEET_BERRY_BUSH && berries.get()) {
            if (state.contains(Properties.AGE_3) && state.get(Properties.AGE_3) == 3) result.hit(Signal.BERRIES, pos.toImmutable());
        } else if (block == Blocks.VINE && vines.get()) {
            if (mc.world.getBlockState(pos.up()).isOf(Blocks.VINE)) return;
            if (run(pos, Direction.DOWN, Blocks.VINE) >= vineLength.get()) result.hit(Signal.VINES, pos.toImmutable());
        } else if (block == Blocks.POINTED_DRIPSTONE && dripstone.get()) {
            if (mc.world.getBlockState(pos.up()).isOf(Blocks.POINTED_DRIPSTONE)) return;
            if (!mc.world.getBlockState(pos.down()).isOf(Blocks.POINTED_DRIPSTONE)) return;
            if (run(pos, Direction.DOWN, Blocks.POINTED_DRIPSTONE) >= dripstoneLength.get()) result.hit(Signal.DRIPSTONE, pos.toImmutable());
        }
    }

    private static boolean isKelp(BlockState s) { return s.isOf(Blocks.KELP) || s.isOf(Blocks.KELP_PLANT); }

    /** Number of consecutive blocks (including the start) of the given types in a direction, capped at 40. */
    private static int run(BlockPos start, Direction dir, Block... types) {
        BlockPos.Mutable p = start.mutableCopy();
        int n = 1;
        while (n < 40) {
            p.move(dir);
            BlockState s = mc.world.getBlockState(p);
            boolean match = false;
            for (Block b : types) if (s.isOf(b)) { match = true; break; }
            if (!match) break;
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ zones

    private void rebuild() {
        scores.keySet().removeIf(k -> mc.world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(k), ChunkPos.getPackedZ(k)) == null);
        double threshold = sensitivity.get() * 5.0;

        Map<Long, Double> flagged = new HashMap<>();   // chunk -> score
        Map<Long, double[]> centroid = new HashMap<>(); // chunk -> {weight, x*weight, z*weight}

        for (var e : scores.entrySet()) {
            ChunkScore s = e.getValue();
            if (s.score >= threshold) {
                flagged.merge(e.getKey(), s.score, Math::max);
                double[] c = centroid.computeIfAbsent(e.getKey(), k -> new double[3]);
                c[0] += s.weightSum; c[1] += s.weightedX; c[2] += s.weightedZ;
            }
        }

        // A hidden geode can straddle chunk borders, so judge it as a whole.
        for (List<BlockPos> geode : clusterGeodes()) {
            double score = Signal.AMETHYST.weight * Math.min(geode.size(), Signal.AMETHYST.cap);
            if (score < threshold) continue;
            for (BlockPos cell : geode) {
                long chunk = ChunkPos.toLong(cell.getX() >> 4, cell.getZ() >> 4);
                flagged.merge(chunk, score, Math::max);
                double[] c = centroid.computeIfAbsent(chunk, k -> new double[3]);
                c[0] += Signal.AMETHYST.weight;
                c[1] += (cell.getX() + 0.5) * Signal.AMETHYST.weight;
                c[2] += (cell.getZ() + 0.5) * Signal.AMETHYST.weight;
            }
        }

        zones = buildZones(flagged, centroid);
        announceNew();
    }

    /** Groups geode cells that are within 5 blocks of each other (Chebyshev distance). */
    private List<List<BlockPos>> clusterGeodes() {
        List<BlockPos> cells = new ArrayList<>();
        for (ChunkScore s : scores.values()) cells.addAll(s.geodeCells);
        if (cells.isEmpty()) return List.of();

        final int cell = 6;
        Map<Long, List<Integer>> grid = new HashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            BlockPos p = cells.get(i);
            grid.computeIfAbsent(BlockPos.asLong(Math.floorDiv(p.getX(), cell), Math.floorDiv(p.getY(), cell), Math.floorDiv(p.getZ(), cell)),
                k -> new ArrayList<>()).add(i);
        }

        int[] parent = new int[cells.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;

        for (int i = 0; i < cells.size(); i++) {
            BlockPos a = cells.get(i);
            int gx = Math.floorDiv(a.getX(), cell), gy = Math.floorDiv(a.getY(), cell), gz = Math.floorDiv(a.getZ(), cell);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                List<Integer> bucket = grid.get(BlockPos.asLong(gx + dx, gy + dy, gz + dz));
                if (bucket == null) continue;
                for (int j : bucket) {
                    if (j <= i) continue;
                    BlockPos b = cells.get(j);
                    int d = Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
                    if (d <= 5) parent[root(parent, i)] = root(parent, j);
                }
            }
        }

        Map<Integer, List<BlockPos>> groups = new HashMap<>();
        for (int i = 0; i < cells.size(); i++) groups.computeIfAbsent(root(parent, i), k -> new ArrayList<>()).add(cells.get(i));
        return new ArrayList<>(groups.values());
    }

    private static int root(int[] parent, int i) {
        while (parent[i] != i) { parent[i] = parent[parent[i]]; i = parent[i]; }
        return i;
    }

    private List<Zone> buildZones(Map<Long, Double> flagged, Map<Long, double[]> centroid) {
        int radius = mergeRadius.get();
        Set<Long> seen = new HashSet<>();
        List<Zone> out = new ArrayList<>();

        for (long seed : flagged.keySet()) {
            if (!seen.add(seed)) continue;
            Set<Long> members = new HashSet<>();
            Deque<Long> frontier = new ArrayDeque<>();
            frontier.add(seed);

            double total = 0, w = 0, wx = 0, wz = 0, fx = 0, fz = 0;
            while (!frontier.isEmpty()) {
                long cur = frontier.poll();
                members.add(cur);
                double score = flagged.get(cur);
                total += score;
                fx += (ChunkPos.getPackedX(cur) * 16 + 8) * score;
                fz += (ChunkPos.getPackedZ(cur) * 16 + 8) * score;
                double[] c = centroid.get(cur);
                if (c != null) { w += c[0]; wx += c[1]; wz += c[2]; }

                int cx = ChunkPos.getPackedX(cur), cz = ChunkPos.getPackedZ(cur);
                for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                    long n = ChunkPos.toLong(cx + dx, cz + dz);
                    if (flagged.containsKey(n) && seen.add(n)) frontier.add(n);
                }
            }
            double x = w > 0 ? wx / w : fx / total;
            double z = w > 0 ? wz / w : fz / total;
            out.add(new Zone(members, x, z, total));
        }
        out.sort(Comparator.comparingDouble(Zone::total).reversed());
        return List.copyOf(out);
    }

    private void announceNew() {
        for (Zone zone : zones) {
            boolean fresh = true;
            for (long m : zone.chunks()) if (announced.contains(m)) { fresh = false; break; }
            announced.addAll(zone.chunks());
            if (!fresh || notify.get() == Notify.Off) continue;

            int x = (int) Math.round(zone.x()), z = (int) Math.round(zone.z());
            int dist = (int) Math.hypot(x - mc.player.getX(), z - mc.player.getZ());
            info("Sus zone: %d blocks away at %d, %d (score %.0f).", dist, x, z, zone.total());
        }
    }

    // ------------------------------------------------------------------ render

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;
        for (Zone zone : zones) {
            for (long chunk : zone.chunks()) {
                double x = ChunkPos.getPackedX(chunk) * 16.0, z = ChunkPos.getPackedZ(chunk) * 16.0;
                event.renderer.box(x, LightIndex.MIN_Y, z, x + 16, LightIndex.MAX_Y, z + 16,
                    sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            }
        }
    }

    @Override
    public String getInfoString() {
        return zones.isEmpty() ? null : String.valueOf(zones.size());
    }
}
