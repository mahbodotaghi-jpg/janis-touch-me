package dev.nitro.schematic;

import net.minecraft.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Non-air blocks of a schematic, normalised so the minimum corner is (0,0,0), sorted bottom-up. */
public class Schematic {
    public record Entry(int x, int y, int z, Block block) {}

    public final String name;
    public final List<Entry> blocks;
    public final int sx, sy, sz;

    private Schematic(String name, List<Entry> blocks, int sx, int sy, int sz) {
        this.name = name; this.blocks = blocks; this.sx = sx; this.sy = sy; this.sz = sz;
    }

    public static Schematic of(String name, List<Entry> raw) {
        if (raw.isEmpty()) return new Schematic(name, List.of(), 0, 0, 0);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (Entry e : raw) {
            minX = Math.min(minX, e.x()); minY = Math.min(minY, e.y()); minZ = Math.min(minZ, e.z());
        }
        List<Entry> list = new ArrayList<>(raw.size());
        int mx = 0, my = 0, mz = 0;
        for (Entry e : raw) {
            int x = e.x() - minX, y = e.y() - minY, z = e.z() - minZ;
            list.add(new Entry(x, y, z, e.block()));
            mx = Math.max(mx, x + 1); my = Math.max(my, y + 1); mz = Math.max(mz, z + 1);
        }
        list.sort(Comparator.comparingInt(Entry::y).thenComparingInt(Entry::z).thenComparingInt(Entry::x));
        return new Schematic(name, list, mx, my, mz);
    }

    /** Rotate around the Y axis in 90 degree steps (block orientation is not changed). */
    public Schematic rotated(int times) {
        int t = ((times % 4) + 4) % 4;
        if (t == 0) return this;
        List<Entry> out = new ArrayList<>(blocks.size());
        for (Entry e : blocks) {
            int nx, nz;
            switch (t) {
                case 1 -> { nx = -e.z(); nz = e.x(); }
                case 2 -> { nx = -e.x(); nz = -e.z(); }
                default -> { nx = e.z(); nz = -e.x(); }
            }
            out.add(new Entry(nx, e.y(), nz, e.block()));
        }
        return of(name, out);
    }
}
