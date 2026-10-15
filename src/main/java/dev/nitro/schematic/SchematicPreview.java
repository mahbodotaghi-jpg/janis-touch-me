package dev.nitro.schematic;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything the builder screen needs to draw a schematic: visible blocks with colours, and the material list. */
public final class SchematicPreview {
    public record Mat(Block block, ItemStack stack, String name, int count) {}

    public final int sx, sy, sz, total;
    public final int[] x, y, z, rgb;
    public final List<Mat> materials;

    private SchematicPreview(int sx, int sy, int sz, int total, int[] x, int[] y, int[] z, int[] rgb, List<Mat> materials) {
        this.sx = sx; this.sy = sy; this.sz = sz; this.total = total;
        this.x = x; this.y = y; this.z = z; this.rgb = rgb;
        this.materials = materials;
    }

    private static long key(int x, int y, int z) {
        return ((long) (y + 1) * 8192L + (z + 1)) * 8192L + (x + 1);
    }

    public static SchematicPreview build(Schematic s) {
        List<Schematic.Entry> all = s.blocks;
        int n = all.size();

        // visible (surface) blocks only; very large schematics are sampled instead
        List<Schematic.Entry> surf = new ArrayList<>();
        if (n <= 250_000) {
            Set<Long> set = new HashSet<>(n * 2);
            for (Schematic.Entry e : all) set.add(key(e.x(), e.y(), e.z()));
            for (Schematic.Entry e : all) {
                int ex = e.x(), ey = e.y(), ez = e.z();
                boolean hidden = set.contains(key(ex + 1, ey, ez)) && set.contains(key(ex - 1, ey, ez))
                        && set.contains(key(ex, ey + 1, ez)) && set.contains(key(ex, ey - 1, ez))
                        && set.contains(key(ex, ey, ez + 1)) && set.contains(key(ex, ey, ez - 1));
                if (!hidden) surf.add(e);
            }
        } else {
            int step = Math.max(1, n / 12000);
            for (int i = 0; i < n; i += step) surf.add(all.get(i));
        }

        int m = surf.size();
        int[] x = new int[m], y = new int[m], z = new int[m], rgb = new int[m];
        for (int i = 0; i < m; i++) {
            Schematic.Entry e = surf.get(i);
            x[i] = e.x(); y[i] = e.y(); z[i] = e.z();
            int c = e.block().getDefaultMapColor().color;
            rgb[i] = c == 0 ? 0x909090 : c;
        }

        Map<Block, Integer> counts = new HashMap<>();
        for (Schematic.Entry e : all) counts.merge(e.block(), 1, Integer::sum);
        List<Mat> mats = new ArrayList<>();
        for (Map.Entry<Block, Integer> en : counts.entrySet()) {
            Block b = en.getKey();
            Item item = b.asItem();
            ItemStack stack = item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
            mats.add(new Mat(b, stack, b.getName().getString(), en.getValue()));
        }
        mats.sort(Comparator.comparingInt(Mat::count).reversed());

        return new SchematicPreview(s.sx, s.sy, s.sz, n, x, y, z, rgb, mats);
    }
}
