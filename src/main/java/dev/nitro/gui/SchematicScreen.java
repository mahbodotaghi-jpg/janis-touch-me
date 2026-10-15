package dev.nitro.gui;

import dev.nitro.module.modules.SchematicBuilder;
import dev.nitro.schematic.Schematic;
import dev.nitro.schematic.SchematicLoader;
import dev.nitro.schematic.SchematicPreview;
import dev.nitro.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Schematic builder: file list | 3D preview (drag to rotate, wheel to zoom, slider to cut layers) | material list.
 * The preview is a voxel view using each block's map colour.
 */
public class SchematicScreen extends Screen {
    private static final int W = 560, H = 310, TITLE = 22, BOTTOM = 40, LEFT = 130, RIGHT = 170, LROW = 14, MROW = 22;
    private static final int MAX_DRAW = 9000;

    private final SchematicBuilder sb;
    private List<String> names;
    private String selectedName, status = "Pick a schematic on the left";
    private Schematic base;
    private volatile SchematicPreview preview;
    private int rot;
    private boolean afk, atCrosshair;

    private float yaw = 35f, pitch = 30f;
    private double zoom = 1.0, listScroll, matScroll;
    private int layerLimit = Integer.MAX_VALUE;
    private boolean dirty = true, draggingView, draggingLayer;
    private float[] ux, uy;
    private int[] col, order;

    private int x0, y0;
    private record Btn(String id, int x, int y, int w, int h) {}
    private final List<Btn> btns = new ArrayList<>();
    private int prevMX, prevMY;

    public SchematicScreen(SchematicBuilder sb) {
        super(Text.literal("Schematic Builder"));
        this.sb = sb;
        sb.refreshFiles();
        this.names = sb.fileNames();
    }

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
    }

    @Override public boolean shouldPause() { return false; }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------- loading ----------
    private void load(String name) {
        selectedName = name;
        base = null;
        preview = null;
        matScroll = 0;
        Path p = sb.pathOf(name);
        if (p == null) { status = "File missing"; return; }
        status = "Loading " + name + "...";
        final int r = rot;

        Thread t = new Thread(() -> {
            try {
                Schematic s = SchematicLoader.load(p);
                SchematicPreview pv = SchematicPreview.build(s.rotated(r));
                MinecraftClient.getInstance().execute(() -> {
                    if (!name.equals(selectedName)) return;
                    base = s;
                    preview = pv;
                    layerLimit = pv.sy;
                    dirty = true;
                    status = name + "   " + s.blocks.size() + " blocks   " + s.sx + "x" + s.sy + "x" + s.sz;
                });
            } catch (Exception ex) {
                MinecraftClient.getInstance().execute(() -> status = "Failed to load: " + ex.getMessage());
            }
        }, "nitro-schematic-preview");
        t.setDaemon(true);
        t.start();
    }

    private void rebuildRotation() {
        Schematic b = base;
        if (b == null) return;
        final int r = rot;
        final String name = selectedName;
        Thread t = new Thread(() -> {
            SchematicPreview pv = SchematicPreview.build(b.rotated(r));
            MinecraftClient.getInstance().execute(() -> {
                if (!name.equals(selectedName)) return;
                preview = pv;
                layerLimit = Math.min(layerLimit, pv.sy);
                dirty = true;
            });
        }, "nitro-schematic-rotate");
        t.setDaemon(true);
        t.start();
    }

    // ---------- 3D ----------
    private void resort(SchematicPreview pv) {
        int n = pv.x.length;
        ux = new float[n]; uy = new float[n]; col = new int[n];
        long[] keys = new long[n];
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        float cyw = (float) Math.cos(yr), syw = (float) Math.sin(yr), cp = (float) Math.cos(pr), sp = (float) Math.sin(pr);
        float cx0 = pv.sx / 2f, cy0 = pv.sy / 2f, cz0 = pv.sz / 2f;
        float radius = (float) Math.sqrt(pv.sx * pv.sx + pv.sy * pv.sy + pv.sz * pv.sz) / 2f + 1f;

        for (int i = 0; i < n; i++) {
            float vx = pv.x[i] + 0.5f - cx0, vy = pv.y[i] + 0.5f - cy0, vz = pv.z[i] + 0.5f - cz0;
            float x1 = vx * cyw - vz * syw;
            float z1 = vx * syw + vz * cyw;
            float y2 = vy * cp + z1 * sp;
            float z2 = -vy * sp + z1 * cp;
            ux[i] = x1;
            uy[i] = y2;

            float depth = Math.max(0f, Math.min(1f, (z2 + radius) / (2f * radius))); // 0 near, 1 far
            float h = pv.y[i] / (float) Math.max(1, pv.sy);
            float shade = (1f - 0.45f * depth) * (0.8f + 0.2f * h);
            int c = pv.rgb[i];
            int r = Math.min(255, (int) (((c >> 16) & 255) * shade));
            int g = Math.min(255, (int) (((c >> 8) & 255) * shade));
            int b = Math.min(255, (int) ((c & 255) * shade));
            col[i] = 0xFF000000 | (r << 16) | (g << 8) | b;

            keys[i] = ((long) (int) ((z2 + 10000f) * 64f) << 32) | i;
        }
        Arrays.sort(keys);
        order = new int[n];
        for (int i = 0; i < n; i++) order[i] = (int) (keys[i] & 0xFFFFFFFFL);
        dirty = false;
    }

    private void drawPreview(DrawContext ctx, int px, int py, int pw, int ph) {
        ctx.fill(px, py, px + pw, py + ph, 0xFF07070C);
        SchematicPreview pv = preview;
        if (pv == null || pv.x.length == 0) {
            String msg = selectedName == null ? "No schematic selected" : status;
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(msg, pw - 12), px + 6, py + ph / 2 - 4, 0xFF777777);
            return;
        }
        if (dirty || order == null || order.length != pv.x.length) resort(pv);

        float maxDim = Math.max(pv.sx, Math.max(pv.sy, pv.sz));
        float scale = (float) (Math.min(pw, ph) / (maxDim * 1.5f) * zoom);
        int size = Math.max(2, (int) Math.ceil(scale * 1.2f));
        int cx = px + pw / 2, cy = py + ph / 2;
        int stride = Math.max(1, (int) Math.ceil(order.length / (double) MAX_DRAW));

        ctx.enableScissor(px, py, px + pw, py + ph - 14);
        for (int k = order.length - 1; k >= 0; k -= stride) {
            int i = order[k];
            if (pv.y[i] >= layerLimit) continue;
            int sx = cx + Math.round(ux[i] * scale) - size / 2;
            int sy = cy - Math.round(uy[i] * scale) - size / 2;
            ctx.fill(sx, sy, sx + size, sy + size, col[i]);
            if (size >= 4) {
                ctx.fill(sx, sy, sx + size, sy + Math.max(1, size / 4), 0x30FFFFFF);
                ctx.fill(sx, sy + size - 1, sx + size, sy + size, 0x40000000);
            }
        }
        ctx.disableScissor();

        // layer slider
        int sy0 = py + ph - 12;
        ctx.fill(px, sy0 - 2, px + pw, py + ph, 0xFF0D0D14);
        int trackX = px + 70, trackW = pw - 80;
        ctx.drawTextWithShadow(textRenderer, "Layers " + Math.min(layerLimit, pv.sy) + "/" + pv.sy, px + 6, sy0 + 1, 0xFF999999);
        ctx.fill(trackX, sy0 + 4, trackX + trackW, sy0 + 6, 0xFF2A2A36);
        int hx = trackX + (int) (trackW * (Math.min(layerLimit, pv.sy) / (double) Math.max(1, pv.sy)));
        ctx.fill(trackX, sy0 + 4, hx, sy0 + 6, Theme.accent(System.currentTimeMillis(), 0));
        ctx.fill(hx - 2, sy0 + 1, hx + 2, sy0 + 9, 0xFFFFFFFF);
    }

    // ---------- materials ----------
    private Map<Item, Integer> inventoryCounts() {
        Map<Item, Integer> m = new HashMap<>();
        if (client == null || client.player == null) return m;
        var inv = client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            var st = inv.getStack(i);
            if (!st.isEmpty()) m.merge(st.getItem(), st.getCount(), Integer::sum);
        }
        return m;
    }

    private void drawMaterials(DrawContext ctx, int px, int py, int pw, int ph) {
        ctx.fill(px, py, px + pw, py + ph, 0xFF0D0D14);
        SchematicPreview pv = preview;
        ctx.fill(px, py, px + pw, py + 16, 0xFF0A0A10);
        long t = System.currentTimeMillis();
        String head = pv == null ? "Materials" : "Materials  " + pv.materials.size() + " types";
        ctx.drawTextWithShadow(textRenderer, head, px + 8, py + 4, Theme.accent(t, 0));
        if (pv == null) return;

        Map<Item, Integer> have = inventoryCounts();
        int listTop = py + 16;
        double max = Math.max(0, pv.materials.size() * MROW + 4 - (ph - 16));
        matScroll = Math.max(0, Math.min(matScroll, max));

        ctx.enableScissor(px, listTop, px + pw, py + ph);
        int ry = listTop + 2 - (int) matScroll;
        for (SchematicPreview.Mat m : pv.materials) {
            if (ry + MROW >= listTop && ry <= py + ph) {
                if (!m.stack().isEmpty()) ctx.drawItem(m.stack(), px + 4, ry + 2);
                ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(m.name(), pw - 34), px + 24, ry + 2, 0xFFEEEEEE);

                int need = m.count();
                int got = m.stack().isEmpty() ? need : have.getOrDefault(m.stack().getItem(), 0);
                int stacks = need / 64, rest = need % 64;
                String needTxt = need + (need >= 64 ? " (" + stacks + "s" + (rest > 0 ? "+" + rest : "") + ")" : "");
                String line = needTxt + "  have " + got;
                ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(line, pw - 30), px + 24, ry + 12,
                        got >= need ? 0xFF55DD88 : 0xFFFF6666);
            }
            ry += MROW;
        }
        ctx.disableScissor();
    }

    // ---------- main render ----------
    private void button(DrawContext ctx, String id, String label, int x, int y, int w, int h, int mx, int my, boolean accent, long t) {
        boolean hov = in(mx, my, x, y, w, h);
        int bg = accent ? Theme.withAlpha(Theme.accent(t, x), hov ? 0xC0 : 0x90) : (hov ? 0xFF262634 : 0xFF1A1A24);
        ctx.fill(x, y, x + w, y + h, bg);
        ctx.drawTextWithShadow(textRenderer, label, x + (w - textRenderer.getWidth(label)) / 2, y + (h - 8) / 2, 0xFFFFFFFF);
        btns.add(new Btn(id, x, y, w, h));
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long t = System.currentTimeMillis();
        prevMX = mx; prevMY = my;
        btns.clear();
        ctx.fill(0, 0, width, height, 0x88000000);

        ctx.fill(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, Theme.withAlpha(Theme.accent(t, 0), 0x70));
        ctx.fill(x0, y0, x0 + W, y0 + H, 0xFF09090F);

        // title
        ctx.fill(x0, y0, x0 + W, y0 + TITLE, 0xFF0D0D14);
        ctx.fill(x0, y0 + TITLE - 1, x0 + W, y0 + TITLE, Theme.withAlpha(Theme.accent(t, 0), 0x90));
        ctx.drawTextWithShadow(textRenderer, "Schematic Builder", x0 + 8, y0 + 7, Theme.accent(t, 0));
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(status, W - 160), x0 + 130, y0 + 7, 0xFF777777);

        int by = y0 + TITLE, bh = H - TITLE - BOTTOM;

        // left: files
        ctx.fill(x0, by, x0 + LEFT, by + bh, 0xFF0A0A10);
        ctx.fill(x0, by, x0 + LEFT, by + 16, 0xFF0D0D14);
        ctx.drawTextWithShadow(textRenderer, "Files", x0 + 8, by + 4, 0xFF999999);
        double maxL = Math.max(0, names.size() * LROW + 4 - (bh - 16));
        listScroll = Math.max(0, Math.min(listScroll, maxL));
        ctx.enableScissor(x0, by + 16, x0 + LEFT, by + bh);
        int ry = by + 18 - (int) listScroll;
        if (names.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, "No files found", x0 + 8, ry + 2, 0xFF666666);
            ctx.drawTextWithShadow(textRenderer, "Put .litematic /", x0 + 8, ry + 16, 0xFF555555);
            ctx.drawTextWithShadow(textRenderer, ".schem / .nbt in", x0 + 8, ry + 26, 0xFF555555);
            ctx.drawTextWithShadow(textRenderer, "nitro/schematics", x0 + 8, ry + 36, 0xFF555555);
        }
        for (String n : names) {
            boolean sel = n.equals(selectedName);
            boolean hov = in(mx, my, x0 + 3, ry, LEFT - 6, LROW - 1) && my >= by + 16 && my < by + bh;
            if (sel) ctx.fill(x0 + 3, ry, x0 + LEFT - 3, ry + LROW - 1, Theme.withAlpha(Theme.accent(t, ry), 0x40));
            else if (hov) ctx.fill(x0 + 3, ry, x0 + LEFT - 3, ry + LROW - 1, 0x18FFFFFF);
            ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(n, LEFT - 14), x0 + 7, ry + 3, sel ? 0xFFFFFFFF : 0xFFAAAAAA);
            ry += LROW;
        }
        ctx.disableScissor();

        // middle: preview, right: materials
        drawPreview(ctx, x0 + LEFT + 1, by, W - LEFT - RIGHT - 2, bh);
        drawMaterials(ctx, x0 + W - RIGHT, by, RIGHT, bh);

        // bottom bar
        int bY = y0 + H - BOTTOM;
        ctx.fill(x0, bY, x0 + W, y0 + H, 0xFF0D0D14);
        int bw = 98, bh2 = 22, gap = 8, bx = x0 + 10, byy = bY + 9;
        button(ctx, "rot", "Rotate: " + (rot * 90) + "\u00B0", bx, byy, bw, bh2, mx, my, false, t); bx += bw + gap;
        button(ctx, "origin", atCrosshair ? "Origin: crosshair" : "Origin: feet", bx, byy, bw + 12, bh2, mx, my, false, t); bx += bw + 12 + gap;
        button(ctx, "mode", afk ? "Mode: Baritone AFK" : "Mode: Assist", bx, byy, bw + 22, bh2, mx, my, false, t); bx += bw + 22 + gap;
        button(ctx, "refresh", "Refresh", bx, byy, 58, bh2, mx, my, false, t);
        boolean running = sb.isEnabled();
        button(ctx, "stop", "Stop", x0 + W - 10 - 60 - 8 - 70, byy, 60, bh2, mx, my, false, t);
        button(ctx, "start", running ? "Restart" : "Start", x0 + W - 10 - 70, byy, 70, bh2, mx, my, true, t);
    }

    // ---------- input ----------
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        for (Btn b : btns) {
            if (!in(mx, my, b.x(), b.y(), b.w(), b.h())) continue;
            switch (b.id()) {
                case "rot" -> { rot = (rot + 1) % 4; rebuildRotation(); }
                case "origin" -> atCrosshair = !atCrosshair;
                case "mode" -> afk = !afk;
                case "refresh" -> { sb.refreshFiles(); names = sb.fileNames(); }
                case "stop" -> { if (sb.isEnabled()) sb.toggle(); }
                case "start" -> {
                    if (selectedName == null) { status = "Pick a schematic first"; return true; }
                    sb.configure(selectedName, afk, atCrosshair, rot);
                    if (sb.isEnabled()) sb.toggle();
                    sb.toggle();
                    close();
                }
                default -> {}
            }
            return true;
        }

        int by = y0 + TITLE, bh = H - TITLE - BOTTOM;
        // file list
        if (in(mx, my, x0, by + 16, LEFT, bh - 16)) {
            int idx = (int) ((my - (by + 18) + listScroll) / LROW);
            if (idx >= 0 && idx < names.size()) load(names.get(idx));
            return true;
        }
        // preview
        int px = x0 + LEFT + 1, pw = W - LEFT - RIGHT - 2;
        if (in(mx, my, px, by, pw, bh)) {
            SchematicPreview pv = preview;
            if (pv != null && my >= by + bh - 14) { draggingLayer = true; setLayer(mx, px, pw, pv); }
            else draggingView = true;
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    private void setLayer(double mx, int px, int pw, SchematicPreview pv) {
        int trackX = px + 70, trackW = pw - 80;
        double f = Math.max(0, Math.min(1, (mx - trackX) / trackW));
        layerLimit = Math.max(1, (int) Math.round(f * pv.sy));
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (draggingView) {
            yaw += (float) dx * 0.6f;
            pitch = Math.max(-85f, Math.min(85f, pitch + (float) dy * 0.6f));
            dirty = true;
            return true;
        }
        if (draggingLayer && preview != null) {
            setLayer(mx, x0 + LEFT + 1, W - LEFT - RIGHT - 2, preview);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        draggingView = draggingLayer = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        int by = y0 + TITLE, bh = H - TITLE - BOTTOM;
        if (in(mx, my, x0, by, LEFT, bh)) { listScroll -= vAmount * 14; return true; }
        if (in(mx, my, x0 + W - RIGHT, by, RIGHT, bh)) { matScroll -= vAmount * 14; return true; }
        if (in(mx, my, x0 + LEFT, by, W - LEFT - RIGHT, bh)) {
            zoom = Math.max(0.3, Math.min(6.0, zoom * Math.pow(1.12, vAmount)));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) { close(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
