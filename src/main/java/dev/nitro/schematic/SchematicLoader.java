package dev.nitro.schematic;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads .litematic (Litematica), .schem (Sponge / WorldEdit) and .nbt (vanilla structure) files. */
public class SchematicLoader {

    public static Schematic load(Path file) throws IOException {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
        List<Schematic.Entry> out = new ArrayList<>();
        if (n.endsWith(".litematic")) litematic(root, out);
        else if (n.endsWith(".schem")) sponge(root, out);
        else structure(root, out);
        return Schematic.of(file.getFileName().toString(), out);
    }

    private static Block blockOf(String name) {
        try {
            int br = name.indexOf('[');
            if (br >= 0) name = name.substring(0, br);
            return Registries.BLOCK.get(Identifier.of(name));
        } catch (Exception e) {
            return Blocks.AIR;
        }
    }

    private static boolean isAir(Block b) {
        return b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR;
    }

    // ---- Litematica ----
    private static void litematic(NbtCompound root, List<Schematic.Entry> out) {
        NbtCompound regions = root.getCompound("Regions");
        for (String key : regions.getKeys()) {
            NbtCompound r = regions.getCompound(key);
            NbtCompound pos = r.getCompound("Position"), size = r.getCompound("Size");
            int sx = size.getInt("x"), sy = size.getInt("y"), sz = size.getInt("z");
            int ax = Math.abs(sx), ay = Math.abs(sy), az = Math.abs(sz);
            int ox = pos.getInt("x") + (sx < 0 ? sx + 1 : 0);
            int oy = pos.getInt("y") + (sy < 0 ? sy + 1 : 0);
            int oz = pos.getInt("z") + (sz < 0 ? sz + 1 : 0);

            NbtList paletteNbt = r.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
            Block[] palette = new Block[paletteNbt.size()];
            for (int i = 0; i < palette.length; i++) palette[i] = blockOf(paletteNbt.getCompound(i).getString("Name"));
            if (palette.length == 0) continue;

            long[] states = r.getLongArray("BlockStates");
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.length - 1));
            long mask = (1L << bits) - 1L;

            for (int y = 0; y < ay; y++) {
                for (int z = 0; z < az; z++) {
                    for (int x = 0; x < ax; x++) {
                        long idx = ((long) y * az + z) * ax + x;
                        int id = readPacked(states, idx, bits, mask);
                        if (id < 0 || id >= palette.length) continue;
                        Block b = palette[id];
                        if (isAir(b)) continue;
                        out.add(new Schematic.Entry(ox + x, oy + y, oz + z, b));
                    }
                }
            }
        }
    }

    private static int readPacked(long[] arr, long idx, int bits, long mask) {
        long start = idx * bits;
        int sa = (int) (start >> 6);
        int ea = (int) (((idx + 1) * bits - 1) >> 6);
        int sb = (int) (start & 63);
        if (sa >= arr.length) return -1;
        if (sa == ea) return (int) ((arr[sa] >>> sb) & mask);
        if (ea >= arr.length) return -1;
        int eo = 64 - sb;
        return (int) (((arr[sa] >>> sb) | (arr[ea] << eo)) & mask);
    }

    // ---- Sponge .schem (v2 and v3) ----
    private static void sponge(NbtCompound root, List<Schematic.Entry> out) {
        NbtCompound s = root.contains("Schematic") ? root.getCompound("Schematic") : root;
        int w = s.getShort("Width") & 0xFFFF, h = s.getShort("Height") & 0xFFFF, l = s.getShort("Length") & 0xFFFF;

        NbtCompound paletteNbt;
        byte[] data;
        if (s.contains("Blocks")) {
            NbtCompound b = s.getCompound("Blocks");
            paletteNbt = b.getCompound("Palette");
            data = b.getByteArray("Data");
        } else {
            paletteNbt = s.getCompound("Palette");
            data = s.getByteArray("BlockData");
        }

        Map<Integer, Block> palette = new HashMap<>();
        for (String key : paletteNbt.getKeys()) palette.put(paletteNbt.getInt(key), blockOf(key));

        long total = (long) w * h * l;
        int i = 0;
        long idx = 0;
        while (i < data.length && idx < total) {
            int value = 0, shift = 0;
            byte bt;
            do {
                bt = data[i++];
                value |= (bt & 0x7F) << shift;
                shift += 7;
            } while ((bt & 0x80) != 0 && i < data.length);

            Block b = palette.get(value);
            if (b != null && !isAir(b)) {
                int x = (int) (idx % w);
                int z = (int) ((idx / w) % l);
                int y = (int) (idx / ((long) w * l));
                out.add(new Schematic.Entry(x, y, z, b));
            }
            idx++;
        }
    }

    // ---- vanilla structure .nbt ----
    private static void structure(NbtCompound root, List<Schematic.Entry> out) {
        NbtList paletteNbt = root.getList("palette", NbtElement.COMPOUND_TYPE);
        Block[] palette = new Block[paletteNbt.size()];
        for (int i = 0; i < palette.length; i++) palette[i] = blockOf(paletteNbt.getCompound(i).getString("Name"));

        NbtList blocks = root.getList("blocks", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < blocks.size(); i++) {
            NbtCompound b = blocks.getCompound(i);
            int state = b.getInt("state");
            if (state < 0 || state >= palette.length) continue;
            Block block = palette[state];
            if (isAir(block)) continue;
            NbtList p = b.getList("pos", NbtElement.INT_TYPE);
            out.add(new Schematic.Entry(p.getInt(0), p.getInt(1), p.getInt(2), block));
        }
    }
}
