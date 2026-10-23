package com.takem.titans.world;

import com.takem.titans.entity.titan.AbstractTitan;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The giant trees of the forest come down as trees. Once something has bitten into one (a thunder spear, a cannon, a
 * falling house, an axe), the whole tree is surveyed a moment later: a trunk with most of its girth gone at some height
 * topples from there, pivoting on what is left of it and falling towards the wound, and a bough that lost its hold on
 * the trunk drops with all its foliage. Leaves left with nothing to grow from flutter away.
 */
@EventBusSubscriber(modid = "takemtitans")
public final class TreeFall {
    private static final Map<ResourceKey<Level>, Map<Long, TreeFall.Hit>> HITS = new HashMap<>();
    /** Below this share of its girth a trunk can't hold the tree up any more. */
    private static final double GIRTH = 0.3;
    private static final int SHOWN_LIMIT = 7000;

    private TreeFall() {
    }

    /** Something just took a block out of a tree: survey it a few ticks from now, once the blast has done its work. */
    public static void notify(ServerLevel level, BlockPos pos, BlockState s) {
        if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES)) {
            GiantTrees.Tree t = GiantTrees.owner(level, pos, s);
            if (t != null) {
                Map<Long, TreeFall.Hit> hits = HITS.computeIfAbsent(level.dimension(), k -> new HashMap<>());
                TreeFall.Hit h = hits.computeIfAbsent((long)t.i() << 32 ^ (long)t.j() & 4294967295L, k -> new TreeFall.Hit(t));
                h.due = level.getGameTime() + 4L;
                h.wounds.add(BlockPos.asLong(pos.getX() >> 3, pos.getY() >> 3, pos.getZ() >> 3));
                if (s.is(BlockTags.LOGS)) {
                    h.x += pos.getX() + 0.5;
                    h.z += pos.getZ() + 0.5;
                    h.n++;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        HITS.clear();
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Map<Long, TreeFall.Hit> hits = HITS.get(level.dimension());
            if (hits != null && !hits.isEmpty()) {
                long now = level.getGameTime();
                Iterator<TreeFall.Hit> it = hits.values().iterator();

                while (it.hasNext()) {
                    TreeFall.Hit h = it.next();
                    if (h.due <= now) {
                        it.remove();
                        survey(level, h);
                        // one tree a tick is plenty
                        return;
                    }
                }
            }
        }
    }

    private static void survey(ServerLevel level, TreeFall.Hit hit) {
        GiantTrees.Tree t = hit.tree;
        int cx = Mth.floor(t.cx());
        int cz = Mth.floor(t.cz());
        int x0 = cx - 31;
        int z0 = cz - 31;
        int y0 = t.base() - 2;
        int sy = t.topY() - y0 + 1;

        for (int x = x0 >> 4; x <= x0 + 62 >> 4; x++) {
            for (int z = z0 >> 4; z <= z0 + 62 >> 4; z++) {
                if (!level.hasChunk(x, z)) {
                    // half a tree is no basis for felling it; the next bite will try again
                    return;
                }
            }
        }

        Long2IntOpenHashMap index = new Long2IntOpenHashMap();
        index.defaultReturnValue(-1);
        LongArrayList pos = new LongArrayList();
        List<BlockState> states = new ArrayList<>();
        IntArrayList shape = new IntArrayList();
        int[] expected = new int[sy];
        int[] present = new int[sy];
        byte[] col = new byte[sy];
        MutableBlockPos m = new MutableBlockPos();

        for (int x = x0; x <= x0 + 62; x++) {
            for (int z = z0; z <= z0 + 62; z++) {
                Arrays.fill(col, (byte)0);
                GiantTrees.shapeColumn(t, x, z, col, y0);
                boolean ownColumn = GiantTrees.near(x + 0.5, z + 0.5) == t;

                for (int k = 0; k < sy; k++) {
                    byte code = col[k];
                    if (code != 0) {
                        if (code == GiantTrees.TRUNK) {
                            expected[k]++;
                        }

                        if (code != GiantTrees.LEAF || ownColumn) {
                            m.set(x, y0 + k, z);
                            BlockState s = level.getBlockState(m);
                            boolean match = code == GiantTrees.LEAF
                                ? s.is(Blocks.SPRUCE_LEAVES) || s.is(Blocks.DARK_OAK_LEAVES)
                                : s.is(Blocks.SPRUCE_LOG) || s.is(Blocks.SPRUCE_WOOD);
                            if (match && !Destruction.isClaimed(level, m.asLong())) {
                                index.put(m.asLong(), pos.size());
                                pos.add(m.asLong());
                                states.add(s);
                                shape.add(code);
                                if (code == GiantTrees.TRUNK) {
                                    present[k]++;
                                }
                            }
                        }
                    }
                }
            }
        }

        int n = pos.size();
        if (n == 0) {
            return;
        }

        // the lowest height at which the trunk has lost most of its girth
        int baseK = t.base() - y0;
        int cut = Integer.MAX_VALUE;

        for (int k = baseK; k < sy; k++) {
            if (expected[k] >= 4 && present[k] < expected[k] * GIRTH) {
                cut = k;
                break;
            }
        }

        // everything still joined to the roots stands
        byte[] state = new byte[n];
        IntArrayList queue = new IntArrayList();

        for (int i = 0; i < n; i++) {
            if (shape.getInt(i) == GiantTrees.TRUNK && BlockPos.getY(pos.getLong(i)) - y0 <= Math.min(baseK, cut)) {
                state[i] = STANDING;
                queue.add(i);
            }
        }

        for (int q = 0; q < queue.size(); q++) {
            long p = pos.getLong(queue.getInt(q));
            int px = BlockPos.getX(p);
            int py = BlockPos.getY(p);
            int pz = BlockPos.getZ(p);

            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int j = index.get(BlockPos.asLong(px + dx, py + dy, pz + dz));
                        if (j >= 0 && state[j] == 0 && shape.getInt(j) != GiantTrees.LEAF && (shape.getInt(j) != GiantTrees.TRUNK || py + dy - y0 <= cut)) {
                            state[j] = STANDING;
                            queue.add(j);
                        }
                    }
                }
            }
        }

        int falling = 0;

        for (int i = 0; i < n; i++) {
            if (state[i] == 0 && shape.getInt(i) != GiantTrees.LEAF) {
                state[i] = FALLING;
                falling++;
            }
        }

        // foliage goes with the nearest wood it grows from
        queue.clear();
        byte[] depth = new byte[n];

        for (int i = 0; i < n; i++) {
            if (shape.getInt(i) != GiantTrees.LEAF) {
                queue.add(i);
            }
        }

        for (int q = 0; q < queue.size(); q++) {
            int i = queue.getInt(q);
            if (depth[i] < 28) {
                long p = pos.getLong(i);

                for (int d = 0; d < 6; d++) {
                    int j = index.get(BlockPos.offset(p, d == 0 ? 1 : (d == 1 ? -1 : 0), d == 2 ? 1 : (d == 3 ? -1 : 0), d == 4 ? 1 : (d == 5 ? -1 : 0)));
                    if (j >= 0 && state[j] == 0) {
                        state[j] = state[i];
                        depth[j] = (byte)(depth[i] + 1);
                        queue.add(j);
                    }
                }
            }
        }

        RandomSource rnd = level.random;
        int orphans = 0;

        for (int i = 0; i < n; i++) {
            if (state[i] == FALLING && shape.getInt(i) != GiantTrees.LEAF) {
                long p = pos.getLong(i);
                hit.wounds.add(BlockPos.asLong(BlockPos.getX(p) >> 3, BlockPos.getY(p) >> 3, BlockPos.getZ(p) >> 3));
            }
        }

        for (int i = 0; i < n; i++) {
            if (state[i] == 0 && near(hit.wounds, pos.getLong(i))) {
                // leaves that lost what they grew from (the generator's own stray tufts elsewhere are left alone)
                m.set(pos.getLong(i));
                if (orphans++ < 600) {
                    Destruction.shred(level, m.immutable(), states.get(i));
                } else {
                    level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
                }
            } else if (state[i] == FALLING && shape.getInt(i) == GiantTrees.LEAF) {
                falling++;
            }
        }

        if (falling == 0) {
            return;
        }

        // the falling wood and foliage, split into the pieces that hang together
        int[] group = new int[n];
        int groups = 0;

        for (int i = 0; i < n; i++) {
            if (state[i] == FALLING && group[i] == 0) {
                int id = ++groups;
                IntArrayList members = new IntArrayList();
                group[i] = id;
                members.add(i);

                for (int q = 0; q < members.size(); q++) {
                    long p = pos.getLong(members.getInt(q));
                    int px = BlockPos.getX(p);
                    int py = BlockPos.getY(p);
                    int pz = BlockPos.getZ(p);

                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                int j = index.get(BlockPos.asLong(px + dx, py + dy, pz + dz));
                                if (j >= 0 && state[j] == FALLING && group[j] == 0) {
                                    group[j] = id;
                                    members.add(j);
                                }
                            }
                        }
                    }
                }

                fell(level, hit, t, y0, cut, members, pos, states, shape, state, group, index, id, rnd);
            }
        }
    }

    private static void fell(
        ServerLevel level,
        TreeFall.Hit hit,
        GiantTrees.Tree t,
        int y0,
        int cut,
        IntArrayList members,
        LongArrayList pos,
        List<BlockState> states,
        IntArrayList shape,
        byte[] state,
        int[] group,
        Long2IntOpenHashMap index,
        int id,
        RandomSource rnd
    ) {
        int size = members.size();
        MutableBlockPos m = new MutableBlockPos();
        if (size < 12) {
            for (int k = 0; k < size; k++) {
                int i = members.getInt(k);
                m.set(pos.getLong(i));
                BlockState s = states.get(i);
                if (s.is(BlockTags.LEAVES)) {
                    Destruction.shred(level, m.immutable(), s);
                } else {
                    level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
                    Destruction.throwBlock(level, m.immutable(), s, new Vec3(0.0, -0.1, 0.0), 0.3F);
                }
            }

            return;
        }

        // wood buried inside the trunk can't be seen while it falls
        boolean[] buried = new boolean[size];
        int shown = size;
        int leaves = 0;

        for (int k = 0; k < size; k++) {
            int i = members.getInt(k);
            if (shape.getInt(i) == GiantTrees.LEAF) {
                leaves++;
            } else if (enclosed(pos.getLong(i), index, group, id)) {
                buried[k] = true;
                shown--;
            }
        }

        // a crown too dense to draw sheds some of its leaves on the way down instead
        float keepLeaf = shown > SHOWN_LIMIT && leaves > 0 ? Math.max(0.0F, 1.0F - (float)(shown - SHOWN_LIMIT) / (float)leaves) : 1.0F;
        LongArrayList shownPos = new LongArrayList();
        List<BlockState> shownStates = new ArrayList<>();
        LongArrayList hidden = new LongArrayList();
        double sx = 0.0;
        double sz = 0.0;
        int lowTrunk = Integer.MAX_VALUE;

        for (int k = 0; k < size; k++) {
            int i = members.getInt(k);
            long p = pos.getLong(i);
            if (buried[k]) {
                hidden.add(p);
            } else if (shape.getInt(i) == GiantTrees.LEAF && keepLeaf < 1.0F && rnd.nextFloat() > keepLeaf) {
                m.set(p);
                level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
                if (rnd.nextInt(6) == 0) {
                    Destruction.sprinkle(level, m.immutable(), states.get(i));
                }
            } else {
                shownPos.add(p);
                shownStates.add(states.get(i));
            }

            if (shape.getInt(i) == GiantTrees.TRUNK) {
                lowTrunk = Math.min(lowTrunk, BlockPos.getY(p));
            }
        }

        Vec3 hinge = null;
        Vec3 tip = null;
        BlockPos hingeBlock = null;
        long[] contacts = new long[0];
        if (lowTrunk != Integer.MAX_VALUE) {
            // what is left of the trunk just below the break, and what went from just above it
            int below = lowTrunk - 1;
            double rx = 0.0;
            double rz = 0.0;
            int rn = 0;
            LongArrayList stump = new LongArrayList();
            double fx = 0.0;
            double fz = 0.0;
            int fn = 0;

            for (int i = 0; i < pos.size(); i++) {
                long p = pos.getLong(i);
                if (shape.getInt(i) == GiantTrees.TRUNK) {
                    int y = BlockPos.getY(p);
                    if (y == below && state[i] == STANDING) {
                        rx += BlockPos.getX(p) + 0.5;
                        rz += BlockPos.getZ(p) + 0.5;
                        rn++;
                        stump.add(p);
                    } else if (y == lowTrunk && group[i] == id) {
                        fx += BlockPos.getX(p) + 0.5;
                        fz += BlockPos.getZ(p) + 0.5;
                        fn++;
                    }
                }
            }

            Vec3 dir = Vec3.ZERO;
            if (rn > 0 && fn > 0) {
                // the tree leans away from what still holds it, into the wound
                dir = new Vec3(fx / fn - rx / rn, 0.0, fz / fn - rz / rn);
            }

            if (dir.lengthSqr() < 0.09 && hit.n > 0) {
                dir = new Vec3(hit.x / hit.n - t.cx(), 0.0, hit.z / hit.n - t.cz());
            }

            if (dir.lengthSqr() < 0.09) {
                double a = rnd.nextDouble() * Math.PI * 2.0;
                dir = new Vec3(Math.cos(a), 0.0, Math.sin(a));
            }

            tip = dir.normalize();
            double r = Math.max(1.0, t.trunkRadius(lowTrunk));
            hinge = new Vec3(t.cx() + tip.x * r, lowTrunk, t.cz() + tip.z * r);
            double best = Double.MAX_VALUE;

            for (int k = 0; k < stump.size(); k++) {
                long p = stump.getLong(k);
                double d = Vec3.atCenterOf(BlockPos.of(p)).distanceToSqr(hinge.x, below + 0.5, hinge.z);
                if (d < best) {
                    best = d;
                    hingeBlock = BlockPos.of(p);
                }
            }

            contacts = new long[Math.min(8, stump.size())];

            for (int k = 0; k < contacts.length; k++) {
                contacts[k] = stump.getLong(rnd.nextInt(stump.size()));
            }
        }

        long[] p = shownPos.toLongArray();
        BlockState[] st = shownStates.toArray(new BlockState[0]);
        long[] h = hidden.toLongArray();
        BlockPos at = BlockPos.of(p[p.length / 2]);
        if (Destruction.queueSection(level, p, st, h, hinge, tip, hingeBlock, contacts, hinge != null ? 26 : 6, true)) {
            BlockPos crack = hinge != null ? BlockPos.containing(hinge) : at;
            level.playSound(null, crack, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 5.0F, 0.35F);
            level.playSound(null, crack, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 4.0F, 0.4F);
            AbstractTitan.shakeNearby(level, Vec3.atCenterOf(crack), 40.0, 0.25F, 12);
        } else {
            for (long b : h) {
                m.set(b);
                level.setBlock(m, Blocks.AIR.defaultBlockState(), 50);
            }

            Destruction.queueLoose(level, p, st, Vec3.atCenterOf(at), true);
        }
    }

    private static boolean near(LongOpenHashSet wounds, long p) {
        int x = BlockPos.getX(p) >> 3;
        int y = BlockPos.getY(p) >> 3;
        int z = BlockPos.getZ(p) >> 3;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (wounds.contains(BlockPos.asLong(x + dx, y + dy, z + dz))) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean enclosed(long p, Long2IntOpenHashMap index, int[] group, int id) {
        for (int d = 0; d < 6; d++) {
            int j = index.get(BlockPos.offset(p, d == 0 ? 1 : (d == 1 ? -1 : 0), d == 2 ? 1 : (d == 3 ? -1 : 0), d == 4 ? 1 : (d == 5 ? -1 : 0)));
            if (j < 0 || group[j] != id) {
                return false;
            }
        }

        return true;
    }

    private static final byte STANDING = 1;
    private static final byte FALLING = 2;

    static final class Hit {
        final GiantTrees.Tree tree;
        final LongOpenHashSet wounds = new LongOpenHashSet();
        long due;
        double x;
        double z;
        int n;

        Hit(GiantTrees.Tree tree) {
            this.tree = tree;
        }
    }
}
