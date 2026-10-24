package com.windblower;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** User-facing values are persisted as entered; simulation-facing values are capped for stability. */
public final class WindBlowerConfig {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("windblower.properties");

    public static volatile double range = 18.0;
    public static volatile double strength = 1.0;
    public static volatile double cone = 2.2;
    public static volatile double lift = 0.12;
    public static volatile double maxSpeed = 6.0;
    public static volatile int particles = 8;
    public static volatile double waterForce = 0.65;
    public static volatile int waterPulseTicks = 8;
    public static volatile boolean blockLaunchEnabled = true;

    private WindBlowerConfig() {}

    public static synchronized void load() {
        if (!Files.exists(FILE)) { save(); return; }
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(FILE)) {
            p.load(reader);
            range = readDouble(p, "range", range, 2, 999);
            strength = readDouble(p, "strength", strength, 0.02, 999);
            cone = readDouble(p, "cone", cone, 0.20, 999);
            lift = readDouble(p, "lift", lift, 0, 999);
            maxSpeed = readDouble(p, "maxspeed", maxSpeed, 0.1, 999);
            particles = readInt(p, "particles", particles, 0, 999);
            waterForce = readDouble(p, "waterforce", waterForce, 0.15, 999);
            waterPulseTicks = readInt(p, "waterpulse", waterPulseTicks, 1, 999);
            blockLaunchEnabled = Boolean.parseBoolean(p.getProperty("blocklaunch", Boolean.toString(blockLaunchEnabled)));
        } catch (IOException ex) {
            WindBlowerMod.LOGGER.error("Could not load settings from {}", FILE, ex);
        }
    }

    private static int readInt(Properties p, String key, int fallback, int min, int max) {
        try { int v = Integer.parseInt(p.getProperty(key, Integer.toString(fallback)).trim()); return v >= min && v <= max ? v : fallback; }
        catch (RuntimeException ex) { return fallback; }
    }
    private static double readDouble(Properties p, String key, double fallback, double min, double max) {
        try { double v = Double.parseDouble(p.getProperty(key, Double.toString(fallback)).trim()); return Double.isFinite(v) && v >= min && v <= max ? v : fallback; }
        catch (RuntimeException ex) { return fallback; }
    }

    public static double effectiveRange() { return Math.min(40.0, range); }
    public static double effectiveCone() { return Math.min(5.0, cone); }
    public static double effectiveStrength() { double root = Math.sqrt(Math.max(0.0, strength)); return 4.0 * root / (root + 2.0); }
    public static double effectiveLift() { return Math.min(0.6, lift); }
    public static double effectiveMaxSpeed() { return Math.min(8.0, maxSpeed); }
    public static int effectiveParticles() { return Math.min(16, particles); }
    public static double effectiveWaterForce() { double root = Math.sqrt(Math.max(0.0, waterForce)); return Math.min(0.25, 0.15 + 0.10 * root / (root + 5.0)); }
    public static int effectiveWaterPulseTicks() { return Math.max(6, waterPulseTicks); }

    public static boolean isValid(String key, double value) {
        if (!Double.isFinite(value)) return false;
        return switch (normalize(key)) {
            case "range" -> value >= 2 && value <= 999;
            case "strength" -> value >= 0.02 && value <= 999;
            case "cone", "width" -> value >= 0.20 && value <= 999;
            case "lift" -> value >= 0 && value <= 999;
            case "maxspeed", "max_speed" -> value >= 0.1 && value <= 999;
            case "particles" -> value >= 0 && value <= 999 && value == Math.rint(value);
            case "waterforce", "water_force" -> value >= 0.15 && value <= 999;
            case "waterpulse", "water_pulse" -> value >= 1 && value <= 999 && value == Math.rint(value);
            case "blocklaunch", "block_launch" -> value == 0 || value == 1;
            default -> false;
        };
    }

    public static synchronized boolean set(String key, double value) {
        String k = normalize(key);
        if (!isValid(k, value)) return false;
        apply(k, value); save(); return true;
    }
    public static synchronized boolean setAll(Map<String, Double> values) {
        for (var e : values.entrySet()) if (!isValid(e.getKey(), e.getValue())) return false;
        for (var e : values.entrySet()) apply(normalize(e.getKey()), e.getValue());
        save(); return true;
    }
    private static void apply(String key, double value) {
        switch (key) {
            case "range" -> range = value;
            case "strength" -> strength = value;
            case "cone", "width" -> cone = value;
            case "lift" -> lift = value;
            case "maxspeed", "max_speed" -> maxSpeed = value;
            case "particles" -> particles = (int) value;
            case "waterforce", "water_force" -> waterForce = value;
            case "waterpulse", "water_pulse" -> waterPulseTicks = (int) value;
            case "blocklaunch", "block_launch" -> blockLaunchEnabled = value == 1;
            default -> throw new IllegalArgumentException("Unknown Wind Blower setting: " + key);
        }
    }
    public static synchronized void setBlockLaunchEnabled(boolean enabled) { blockLaunchEnabled = enabled; save(); }
    private static String normalize(String key) { return key.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_'); }

    public static synchronized void reset() {
        range = 18.0; strength = 1.0; cone = 2.2; lift = 0.12; maxSpeed = 6.0;
        particles = 8; waterForce = 0.65; waterPulseTicks = 8; blockLaunchEnabled = true; save();
    }
    public static synchronized void save() {
        Properties p = new Properties();
        p.setProperty("range", Double.toString(range)); p.setProperty("strength", Double.toString(strength));
        p.setProperty("cone", Double.toString(cone)); p.setProperty("lift", Double.toString(lift));
        p.setProperty("maxspeed", Double.toString(maxSpeed)); p.setProperty("particles", Integer.toString(particles));
        p.setProperty("waterforce", Double.toString(waterForce)); p.setProperty("waterpulse", Integer.toString(waterPulseTicks));
        p.setProperty("blocklaunch", Boolean.toString(blockLaunchEnabled));
        try { Files.createDirectories(FILE.getParent()); try (Writer writer = Files.newBufferedWriter(FILE)) { p.store(writer, "Wind Blower v1.4.0 settings; runtime caps protect physics performance."); } }
        catch (IOException ex) { WindBlowerMod.LOGGER.error("Could not save settings to {}", FILE, ex); }
    }
    public static String valueFor(String key) {
        return switch (normalize(key)) {
            case "range" -> Double.toString(range); case "strength" -> Double.toString(strength);
            case "cone", "width" -> Double.toString(cone); case "lift" -> Double.toString(lift);
            case "maxspeed", "max_speed" -> Double.toString(maxSpeed); case "particles" -> Integer.toString(particles);
            case "waterforce", "water_force" -> Double.toString(waterForce); case "waterpulse", "water_pulse" -> Integer.toString(waterPulseTicks);
            case "blocklaunch", "block_launch" -> blockLaunchEnabled ? "1" : "0";
            default -> "unknown";
        };
    }
}
