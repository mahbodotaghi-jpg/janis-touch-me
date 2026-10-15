package dev.nitro.module.modules;

import dev.nitro.module.Category;
import dev.nitro.gui.SchematicScreen;
import dev.nitro.module.Module;
import dev.nitro.schematic.Schematic;
import dev.nitro.schematic.SchematicLoader;
import dev.nitro.setting.ActionSetting;
import dev.nitro.setting.BooleanSetting;
import dev.nitro.setting.ModeSetting;
import dev.nitro.setting.NumberSetting;
import dev.nitro.util.BaritoneBridge;
import dev.nitro.util.InvUtil;
import dev.nitro.util.RenderUtil;
import dev.nitro.util.Rotations;
import dev.nitro.util.Safe;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;

/**
 * Builds a schematic block by block around you. Put .litematic / .schem / .nbt files in
 * .minecraft/nitro/schematics (or the Litematica folder .minecraft/schematics), pick one in the settings,
 * enable the module and walk around the build area; it places what is in reach and shows a preview.
 * Mode "Assist": NITRO places what is in reach while you walk (block types only, no orientation).
 * Mode "Baritone AFK": hands the build to Baritone, which walks around and builds it for you.
 */
public class SchematicBuilder extends Module {
    private final ActionSetting openGui = add(new ActionSetting("Open builder GUI", () -> mc.setScreen(new SchematicScreen(this))));
    private final ModeSetting mode = add(new ModeSetting("Mode", "Assist", "Baritone AFK"));
    private final ModeSetting file = add(new ModeSetting("File", "(none)"));
    private final ModeSetting originMode = add(new ModeSetting("Origin", "At feet", "At crosshair"));
    private final NumberSetting rotation = add(new NumberSetting("Rotation", 0, 0, 3, 1));
    private final NumberSetting range = add(new NumberSetting("Range", 4.0, 2, 4.8, 0.1));
    private final NumberSetting delay = add(new NumberSetting("Delay (ticks)", 2, 0, 10, 1));
    private final NumberSetting perCycle = add(new NumberSetting("Blocks per cycle", 1, 1, 6, 1));
    private final BooleanSetting preview = add(new BooleanSetting("Preview", true));

    private final Map<String, Path> files = new LinkedHashMap<>();
    private final Set<Item> reportedMissing = new HashSet<>();
    private volatile Schematic schem;
    private BlockPos origin;
    private int timer, previewTick;
    private boolean baritoneRunning;
    private int grace, statusTick;
    private List<Box> missing = List.of(), wrong = List.of();

    public SchematicBuilder() {
        super("SchematicBuilder", "Places blocks from a .litematic/.schem/.nbt file.", Category.WORLD);
        refreshFiles();
    }

    // ---------- files ----------
    public void refreshFiles() {
        files.clear();
        Path game = FabricLoader.getInstance().getGameDir();
        Path nitro = game.resolve("nitro").resolve("schematics");
        try { Files.createDirectories(nitro); } catch (IOException ignored) {}
        scan(game.resolve("schematics"));
        scan(nitro);
        if (files.isEmpty()) file.setModes("(none)");
        else file.setModes(files.keySet().toArray(new String[0]));
    }

    private void scan(Path dir) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isRegularFile).sorted().forEach(p -> {
                String n = p.getFileName().toString();
                String l = n.toLowerCase(Locale.ROOT);
                if (l.endsWith(".litematic") || l.endsWith(".schem") || l.endsWith(".nbt")) files.put(n, p);
            });
        } catch (IOException ignored) {}
    }

    private void msg(String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("§b[NITRO]§r " + s), false);
    }

    // ---------- API for the builder screen ----------
    public List<String> fileNames() { return new ArrayList<>(files.keySet()); }

    public Path pathOf(String name) { return files.get(name); }

    public void configure(String fileName, boolean afk, boolean atCrosshair, int rot) {
        file.select(fileName);
        mode.select(afk ? "Baritone AFK" : "Assist");
        originMode.select(atCrosshair ? "At crosshair" : "At feet");
        rotation.set((double) rot);
    }

    // ---------- lifecycle ----------
    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) { toggle(); return; }
        refreshFiles();
        Path path = files.get(file.get());
        if (path == null) {
            msg("No schematics found. Put .litematic / .schem / .nbt files in .minecraft/nitro/schematics");
            toggle();
            return;
        }

        origin = mc.player.getBlockPos();
        if (originMode.get().equals("At crosshair")
                && mc.crosshairTarget instanceof BlockHitResult h && h.getType() == HitResult.Type.BLOCK) {
            origin = h.getBlockPos().offset(h.getSide());
        }

        schem = null;
        reportedMissing.clear();
        missing = wrong = List.of();
        timer = previewTick = 0;
        boolean afk = mode.get().equals("Baritone AFK");
        if (afk && !startBaritone(path)) { toggle(); return; }
        final int rot = afk ? 0 : (int) Math.round(rotation.get());
        msg("Loading " + path.getFileName() + "...");

        Thread t = new Thread(() -> {
            try {
                Schematic s = SchematicLoader.load(path).rotated(rot);
                mc.execute(() -> {
                    if (!isEnabled()) return;
                    schem = s;
                    msg("Loaded " + s.blocks.size() + " blocks (" + s.sx + "x" + s.sy + "x" + s.sz + "). Walk around the area to build.");
                });
            } catch (Exception ex) {
                mc.execute(() -> {
                    msg("Failed to load schematic: " + ex.getMessage());
                    if (isEnabled()) toggle();
                });
            }
        }, "nitro-schematic-loader");
        t.setDaemon(true);
        t.start();
    }

    private boolean startBaritone(Path path) {
        if (!BaritoneBridge.available()) {
            msg("Baritone isn't installed. Put the Baritone 1.21.4 Fabric jar (baritone-api-fabric) in your mods folder to use AFK mode.");
            return false;
        }
        try {
            // Baritone reads schematics from .minecraft/schematics and can't handle spaces in the name
            Path dir = FabricLoader.getInstance().getGameDir().resolve("schematics");
            Files.createDirectories(dir);
            String name = path.getFileName().toString().replace(' ', '_');
            Path dest = dir.resolve(name);
            if (!dest.toAbsolutePath().normalize().equals(path.toAbsolutePath().normalize())) {
                Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            boolean ok = BaritoneBridge.execute("build " + name + " " + origin.getX() + " " + origin.getY() + " " + origin.getZ());
            if (!ok) { msg("Baritone didn't accept the build command."); return false; }
            baritoneRunning = true;
            grace = 60;
            statusTick = 0;
            msg("Baritone is building " + name + " at " + origin.getX() + " " + origin.getY() + " " + origin.getZ()
                    + ". Keep the materials in your inventory.");
            return true;
        } catch (IOException e) {
            msg("Couldn't prepare the schematic: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void onDisable() {
        if (baritoneRunning) {
            BaritoneBridge.execute("cancel");
            baritoneRunning = false;
        }
        schem = null;
        origin = null;
        missing = wrong = List.of();
    }

    // ---------- building ----------
    private static int lowerBound(List<Schematic.Entry> l, int y) {
        int lo = 0, hi = l.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (l.get(mid).y() < y) lo = mid + 1; else hi = mid;
        }
        return lo;
    }

    @Override
    public void onTick() {
        if (baritoneRunning && mc.player != null) {
            if (grace > 0) grace--;
            else if (statusTick++ % 20 == 0 && !BaritoneBridge.builderActive()) {
                baritoneRunning = false;
                msg("Baritone finished or stopped building.");
                toggle();
                return;
            }
        }

        Schematic s = schem;
        if (s == null || origin == null || mc.player == null || mc.world == null || mc.interactionManager == null) return;

        Vec3d eye = mc.player.getEyePos();
        updatePreview(s, eye);
        if (baritoneRunning) return; // Baritone does the placing

        if (mc.currentScreen != null) return;
        if (timer-- > 0) return;
        timer = Safe.delay((int) Math.round(delay.get()));

        double r = Safe.blockReach(range.get());
        double r2 = r * r;
        int maxPlace = (int) Math.round(perCycle.get());
        int placed = 0;
        List<Schematic.Entry> list = s.blocks;
        int start = lowerBound(list, (int) Math.floor(eye.y - r) - origin.getY());

        for (int i = start; i < list.size() && placed < maxPlace; i++) {
            Schematic.Entry e = list.get(i);
            if (origin.getY() + e.y() > eye.y + r) break;

            BlockPos p = origin.add(e.x(), e.y(), e.z());
            double dx = p.getX() + 0.5 - eye.x, dy = p.getY() + 0.5 - eye.y, dz = p.getZ() + 0.5 - eye.z;
            if (dx * dx + dy * dy + dz * dz > r2) continue;

            BlockState cur = mc.world.getBlockState(p);
            if (cur.isOf(e.block())) continue;
            if (!cur.isAir() && !cur.isReplaceable()) continue;
            int res = tryPlace(p, e.block());
            if (res == 1) placed++;
            else if (res == 2) { timer = 0; break; } // still turning toward the block
        }
    }

    /** 0 = skipped, 1 = placed, 2 = still turning toward the block */
    private int tryPlace(BlockPos p, Block block) {
        Item item = block.asItem();
        if (item == Items.AIR) return 0;

        BlockPos support = null;
        Direction face = null;
        for (Direction d : Direction.values()) {
            BlockPos n = p.offset(d);
            BlockState ns = mc.world.getBlockState(n);
            if (ns.isAir() || ns.isReplaceable() || !ns.getFluidState().isEmpty()) continue;
            if (ns.createScreenHandlerFactory(mc.world, n) != null) continue; // don't click containers
            support = n;
            face = d.getOpposite();
            break;
        }
        if (support == null) return 0;

        if (!hold(item)) {
            if (reportedMissing.add(item)) msg("Out of " + item.getName().getString());
            return 0;
        }

        Vec3d hitPos = Vec3d.ofCenter(support).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
        if (Safe.rotate() && !Rotations.aim(hitPos)) return 2;
        BlockHitResult hit = new BlockHitResult(hitPos, face, support, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        return 1;
    }

    private boolean hold(Item item) {
        PlayerInventory inv = mc.player.getInventory();
        if (inv.getMainHandStack().isOf(item)) return true;
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(item)) { InvUtil.selectSlot(i); return true; }
        }
        for (int i = 9; i < 36; i++) {
            if (inv.getStack(i).isOf(item)) {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i, inv.selectedSlot, SlotActionType.SWAP, mc.player);
                return true;
            }
        }
        return false;
    }

    // ---------- preview ----------
    private void updatePreview(Schematic s, Vec3d eye) {
        if (!preview.get() || previewTick++ % 10 != 0) return;
        List<Box> m = new ArrayList<>(), w = new ArrayList<>();
        List<Schematic.Entry> list = s.blocks;
        int start = lowerBound(list, (int) Math.floor(eye.y - 12) - origin.getY());

        for (int i = start; i < list.size() && m.size() < 700 && w.size() < 700; i++) {
            Schematic.Entry e = list.get(i);
            if (origin.getY() + e.y() > eye.y + 12) break;
            BlockPos p = origin.add(e.x(), e.y(), e.z());
            double dx = p.getX() + 0.5 - eye.x, dz = p.getZ() + 0.5 - eye.z;
            if (dx * dx + dz * dz > 24 * 24) continue;

            BlockState cur = mc.world.getBlockState(p);
            if (cur.isOf(e.block())) continue;
            Box box = new Box(p).expand(-0.12);
            if (cur.isAir() || cur.isReplaceable()) m.add(box); else w.add(box);
        }
        missing = m;
        wrong = w;
    }

    @Override
    public void onRender(WorldRenderContext ctx) {
        Schematic s = schem;
        if (!preview.get() || s == null || origin == null) return;

        RenderUtil.Batch b = new RenderUtil.Batch(ctx);
        b.outline(new Box(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + s.sx, origin.getY() + s.sy, origin.getZ() + s.sz), 0xFFFFFFFF);
        for (Box box : missing) { b.outline(box, 0xFF55E0FF); b.fill(box, 0x2855E0FF); }
        for (Box box : wrong) b.outline(box, 0xFFFF5555);
        b.draw();
    }
}
