package com.windblower;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Compact, scrollable graphite/teal physics panel with eased meter animations and responsive sizing. */
public final class WindBlowerSettingsScreen extends Screen {
    private record Setting(String key, String label, String unit) {}
    private static final Setting[][] PAGES = {
            {new Setting("range", "Influence distance", "blocks"), new Setting("strength", "Impulse strength", "power"),
             new Setting("cone", "Field width", "blocks"), new Setting("lift", "Vertical lift", "up/tick"),
             new Setting("maxspeed", "Velocity ceiling", "blocks/tick")},
            {new Setting("particles", "Spray particles", "count"), new Setting("waterforce", "Water reaction", "radius"),
             new Setting("waterpulse", "Fluid pulse interval", "ticks")}
    };
    private static final String[] PAGE_NAMES = {"WIND FIELD", "FLUID / FX"};
    private static final int OUTER = 0xF00D1118, PANEL = 0xF0151C26, PANEL_TOP = 0xFF1C2734;
    private static final int ROW_A = 0xFF1D2733, ROW_B = 0xFF18222D, TEXT = 0xFFF0F5FA;
    private static final int MUTED = 0xFF91A2B4, TEAL = 0xFF5CE1D1, BLUE = 0xFF64A9FF;
    private static final int AMBER = 0xFFFFC66D, RED = 0xFFFF737A;

    private final Screen parent;
    private final int page;
    private EditBox[] fields = new EditBox[0];
    private int[] visibleIndices = new int[0];
    private float[] meterProgress = new float[0];
    private int scrollOffset;
    private String message = "";
    private int messageColor = TEAL;
    private float activeGlow;

    public WindBlowerSettingsScreen(Screen parent) { this(parent, 0); }
    public WindBlowerSettingsScreen(Screen parent, int page) {
        super(Component.literal("Wind Blower · Physics Console"));
        this.parent = parent;
        this.page = Math.max(0, Math.min(PAGES.length - 1, page));
    }

    private int panelWidth() { return Math.max(1, Math.min(650, width - 16)); }
    private int left() { return (width - panelWidth()) / 2; }
    private int bodyTop() { return 91; }
    private int bodyBottom() { return Math.max(bodyTop() + 22, height - 66); }
    private int visibleRowCount() {
        int space = Math.max(25, bodyBottom() - bodyTop());
        return Math.max(1, Math.min(PAGES[page].length, space / 39));
    }
    private int rowHeight() { return Math.max(27, Math.min(40, (bodyBottom() - bodyTop()) / visibleRowCount())); }
    private void clampScroll() { scrollOffset = Math.max(0, Math.min(scrollOffset, PAGES[page].length - visibleRowCount())); }

    @Override protected void init() {
        clampScroll();
        int l = left(), w = panelWidth();
        int side = 12, gap = 6, tabW = Math.max(32, (w - side * 2 - gap) / 2);
        for (int i = 0; i < PAGE_NAMES.length; i++) {
            final int target = i;
            this.addRenderableWidget(Button.builder(Component.literal(PAGE_NAMES[i] + (i == page ? "  ◆" : "")), b -> switchPage(target))
                    .bounds(l + side + i * (tabW + gap), 61, tabW, 22).build());
        }

        Setting[] settings = PAGES[page];
        int count = visibleRowCount(), rowH = rowHeight();
        visibleIndices = new int[count]; fields = new EditBox[count]; meterProgress = new float[count];
        int fieldW = Math.max(52, Math.min(112, w / 5));
        int fieldX = l + w - fieldW - 19;
        for (int i = 0; i < count; i++) {
            int index = scrollOffset + i; visibleIndices[i] = index;
            Setting setting = settings[index];
            int rowY = bodyTop() + i * rowH;
            EditBox field = new EditBox(font, fieldX, rowY + 6, fieldW, Math.min(20, rowH - 10), Component.literal(setting.label()));
            field.setMaxLength(12);
            field.setValue(WindBlowerConfig.valueFor(setting.key()));
            fields[i] = addRenderableWidget(field);
            meterProgress[i] = (float) ratioFor(setting.key());
        }

        int footerY = height - 27;
        int btnGap = 5;
        int btnW = Math.max(36, Math.min(100, (w - 28 - btnGap * 2) / 3));
        int total = btnW * 3 + btnGap * 2, start = (width - total) / 2;
        addRenderableWidget(Button.builder(Component.literal("APPLY"), b -> savePage()).bounds(start, footerY, btnW, 22).build());
        addRenderableWidget(Button.builder(Component.literal("RESET"), b -> {
            WindBlowerConfig.reset(); scrollOffset = 0; setMessage("Factory profile restored.", true); rebuild();
        }).bounds(start + btnW + btnGap, footerY, btnW, 22).build());
        addRenderableWidget(Button.builder(Component.literal("DONE"), b -> { if (savePage()) onClose(); })
                .bounds(start + (btnW + btnGap) * 2, footerY, btnW, 22).build());

        int toggleY = height - 62;
        int toggleGap = 6;
        int toggleWidth = Math.max(42, (w - 32 - toggleGap) / 2);
        addRenderableWidget(Button.builder(Component.literal("BLOCK LAUNCH  ·  " + (WindBlowerConfig.blockLaunchEnabled ? "ON" : "OFF")), b -> {
            if (!savePage()) return;
            WindBlowerConfig.setBlockLaunchEnabled(!WindBlowerConfig.blockLaunchEnabled);
            setMessage("Block launch " + (WindBlowerConfig.blockLaunchEnabled ? "enabled" : "disabled") + ".", true);
            rebuild();
        }).bounds(l + 13, toggleY, toggleWidth, 19).build());
        addRenderableWidget(Button.builder(Component.literal("Z GUST  ·  " + (WindBlowerClient.isUseModeEnabled() ? "ARMED" : "OFF")), b -> {
            if (minecraft.player != null) minecraft.player.connection.sendCommand(WindBlowerClient.isUseModeEnabled() ? "windblow reUse" : "windblow use");
        }).bounds(l + 13 + toggleWidth + toggleGap, toggleY, toggleWidth, 19).build());
    }

    private void rebuild() { clearWidgets(); init(); }
    private boolean savePage() {
        Map<String, Double> values = new LinkedHashMap<>();
        for (int i = 0; i < fields.length; i++) {
            Setting setting = PAGES[page][visibleIndices[i]];
            try {
                double value = Double.parseDouble(fields[i].getValue().trim());
                if (!WindBlowerConfig.isValid(setting.key(), value)) { setMessage("Invalid value for " + setting.key() + ".", false); return false; }
                values.put(setting.key(), value);
            } catch (NumberFormatException ex) { setMessage("Enter a number in every visible field.", false); return false; }
        }
        if (!WindBlowerConfig.setAll(values)) { setMessage("Settings were not saved; check the values.", false); return false; }
        setMessage("Saved · changes are active.", true); return true;
    }
    private void switchPage(int next) {
        if (next == page) return;
        if (savePage()) minecraft.gui.setScreen(new WindBlowerSettingsScreen(parent, next));
    }
    private void setMessage(String text, boolean success) { message = text; messageColor = success ? TEAL : RED; }
    private void fitText(GuiGraphicsExtractor g, String text, int x, int y, int maxWidth, int color, boolean shadow) {
        if (maxWidth < 1) return;
        String out = text;
        while (!out.isEmpty() && font.width(out) > maxWidth) out = out.substring(0, out.length() - 1);
        if (!out.equals(text) && !out.isEmpty()) out = out.substring(0, Math.max(0, out.length() - 1)) + "…";
        g.text(font, out, x, y, color, shadow);
    }
    private double ratioFor(String key) {
        return switch (key) {
            case "range" -> Math.min(1.0, WindBlowerConfig.effectiveRange() / 40.0);
            case "strength" -> WindBlowerConfig.effectiveStrength() / 4.0;
            case "cone" -> WindBlowerConfig.effectiveCone() / 5.0;
            case "lift" -> WindBlowerConfig.effectiveLift() / 0.6;
            case "maxspeed" -> WindBlowerConfig.effectiveMaxSpeed() / 8.0;
            case "particles" -> WindBlowerConfig.effectiveParticles() / 16.0;
            case "waterforce" -> WindBlowerConfig.effectiveWaterForce() / 0.25;
            case "waterpulse" -> Math.min(1.0, WindBlowerConfig.effectiveWaterPulseTicks() / 24.0);
            default -> 0.0;
        };
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int l = left(), w = panelWidth(), right = l + w;
        g.fill(0, 0, width, height, 0xD9070B10);
        g.fill(l, 5, right, height - 5, OUTER);
        g.fill(l + 2, 7, right - 2, 55, PANEL_TOP);
        g.fill(l + 9, 57, right - 9, 86, PANEL);
        g.fill(l + 9, bodyTop() - 3, right - 9, bodyBottom(), PANEL);
        g.fill(l + 9, height - 65, right - 9, height - 31, PANEL);
        g.fill(l, 5, right, 7, TEAL);
        g.fill(l + 10, 52, right - 10, 54, 0xFF293949);
        g.fill(l + 10, 52, l + 10 + (int) ((w - 20) * (0.25 + 0.08 * Math.sin(System.currentTimeMillis() / 900.0))), 54, TEAL);

        for (int i = 0; i < visibleIndices.length; i++) {
            int y = bodyTop() + i * rowHeight();
            g.fill(l + 13, y, right - 13, Math.min(bodyBottom() - 1, y + rowHeight() - 2), (i & 1) == 0 ? ROW_A : ROW_B);
            g.fill(l + 13, y, l + 15, Math.min(bodyBottom() - 1, y + rowHeight() - 2), i == 0 ? TEAL : 0xFF304152);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);

        String title = "WIND BLOWER";
        g.text(font, title, l + 16, 15, TEXT, true);
        String sub = "PHYSICS CONSOLE   /   FIELD CONTROL  1.4.0";
        fitText(g, sub, l + 16, 32, w - 33, MUTED, false);
        activeGlow += ((WindBlowerClient.isUseModeEnabled() ? 1f : 0f) - activeGlow) * 0.16f;
        int dotColor = WindBlowerClient.isUseModeEnabled() ? TEAL : 0xFF657384;
        g.fill(right - 103, 17, right - 95, 25, dotColor);
        fitText(g, WindBlowerClient.isUseModeEnabled() ? "Z FIELD ACTIVE" : "Z FIELD STANDBY", right - 88, 17, 76, dotColor, true);
        fitText(g, "LIVE", right - 44, 33, 28, activeGlow > 0.01f ? TEAL : MUTED, false);

        Setting[] settings = PAGES[page];
        for (int i = 0; i < fields.length; i++) {
            int rowY = bodyTop() + i * rowHeight();
            Setting setting = settings[visibleIndices[i]];
            int labelMax = fields[i].getX() - (l + 26);
            fitText(g, setting.label(), l + 22, rowY + 6, labelMax, TEXT, false);
            fitText(g, setting.unit().toUpperCase(), l + 22, rowY + 19, labelMax, MUTED, false);
            float target = (float) ratioFor(setting.key());
            meterProgress[i] += (target - meterProgress[i]) * 0.18f;
            int barL = l + 22, barR = Math.max(barL + 1, fields[i].getX() - 9), barY = rowY + rowHeight() - 5;
            g.fill(barL, barY, barR, barY + 2, 0xFF0D131B);
            g.fill(barL, barY, barL + Math.max(0, (int) ((barR - barL) * Math.max(0f, Math.min(1f, meterProgress[i])))), barY + 2, setting.key().startsWith("water") ? BLUE : TEAL);
        }
        if (scrollOffset > 0 || scrollOffset + visibleIndices.length < settings.length) {
            int trackTop = bodyTop() + 2, trackBottom = bodyBottom() - 4;
            int trackX = right - 17;
            g.fill(trackX, trackTop, trackX + 2, trackBottom, 0xFF0B1118);
            int thumbH = Math.max(10, (trackBottom - trackTop) * visibleIndices.length / settings.length);
            int maxTrack = Math.max(1, trackBottom - trackTop - thumbH);
            int thumbY = trackTop + (int) (maxTrack * (scrollOffset / (double) Math.max(1, settings.length - visibleIndices.length)));
            g.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbH, BLUE);
        }
        String info = message.isEmpty() ? (page == 0 ? "ENTITY IMPULSE  ·  REALISTIC PHYSICS BODY SUPPORT" : "WATER BRIDGE  ·  SMALL RADIUS / RATE LIMITED") : message;
        fitText(g, info, l + 15, height - 38, w - 30, message.isEmpty() ? MUTED : messageColor, false);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxOffset = PAGES[page].length - visibleRowCount();
        if (maxOffset <= 0 || verticalAmount == 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        int next = Math.max(0, Math.min(maxOffset, scrollOffset + (verticalAmount < 0 ? 1 : -1)));
        if (next != scrollOffset) {
            if (!savePage()) return true;
            scrollOffset = next;
            rebuild();
        }
        return true;
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
