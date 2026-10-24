package com.windblower;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.Locale;

/** Safe client bridge for the public classes exposed by Realistic-Water branch 26.3-1.2.3. */
public final class WindBlowerWaterCompat {
    private static final String WORLD_CLASS = "me.skaffy.client.water.WaterWorld";
    private static final String WATER_FLUID_CLASS = "me.skaffy.fluid.RealisticWaterFluid";
    private static final String LAVA_FLUID_CLASS = "me.skaffy.fluid.RealisticLavaFluid";
    private static final String SLIME_FLUID_CLASS = "me.skaffy.fluid.RealisticSlimeFluid";
    private static volatile Api cachedApi;
    private static volatile boolean warnedAboutApi;
    private static long lastWaterPulseTick = Long.MIN_VALUE / 2;

    private enum Material { NONE, WATER, LAVA, SLIME, SAND, LEAVES }
    private record Api(Object world, Method onExplosion, Method waterPredicate, Method lavaPredicate, Method slimePredicate) {}
    private WindBlowerWaterCompat() {}

    public static void pulse(Level level, Player player, Vec3 origin, Vec3 look, long gameTick) {
        if (!level.isClientSide() || !FabricLoader.getInstance().isModLoaded("realistic-water")) return;
        int interval = WindBlowerConfig.effectiveWaterPulseTicks();
        if (WindBlowerConfig.waterForce <= 0.0 || gameTick - lastWaterPulseTick < interval) return;

        try {
            Vec3 direction = look.normalize();
            Vec3 end = origin.add(direction.scale(WindBlowerConfig.effectiveRange()));
            BlockHitResult hit = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
            if (hit.getType() != HitResult.Type.BLOCK) return;
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            FluidState fluid = state.getFluidState();
            Material material = classify(fluid, state);
            if (material == Material.NONE) return;

            if (WindBlowerConfig.effectiveParticles() > 0) spawnSpray(level, hit.getLocation(), direction, material);
            Api api = api();
            if (api == null || !isCompatibleMaterial(api, fluid, state, material)) return;

            // Upstream 1.2.3 only exposes a radial blast-reaction hook. Keep its radius bounded and rate-limit it;
            // raw GUI values are never passed into the water simulation.
            float radius = (float) Math.max(0.15, WindBlowerConfig.effectiveWaterForce());
            Vec3 center = hit.getLocation().subtract(direction.scale(Math.max(0.15, radius * 0.45)));
            api.onExplosion().invoke(api.world(), center, radius);
            lastWaterPulseTick = gameTick;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            if (!warnedAboutApi) {
                warnedAboutApi = true;
                WindBlowerMod.LOGGER.warn("Ultra Realistic Water 26.3-1.2.3 bridge unavailable; vanilla entity gusts remain enabled.", ex);
            }
        }
    }

    private static Material classify(FluidState fluid, BlockState state) {
        if (!fluid.isEmpty()) {
            if (fluid.is(FluidTags.WATER)) return Material.WATER;
            if (fluid.is(FluidTags.LAVA)) return Material.LAVA;
            try {
                Api api = api();
                if (api != null) {
                    if (matches(api.waterPredicate(), fluid)) return Material.WATER;
                    if (matches(api.lavaPredicate(), fluid)) return Material.LAVA;
                    if (matches(api.slimePredicate(), fluid)) return Material.SLIME;
                }
            } catch (ReflectiveOperationException ignored) { }
        }
        String name = state.getBlock().getClass().getName().toLowerCase(Locale.ROOT);
        if (name.contains("realisticwaterblock")) return Material.WATER;
        if (name.contains("realisticlavablock")) return Material.LAVA;
        if (name.contains("realisticslimeblock")) return Material.SLIME;
        if (name.contains("realisticsandblock") || name.contains("realisticsand")) return Material.SAND;
        if (name.contains("realisticleavesblock")) return Material.LEAVES;
        return Material.NONE;
    }

    private static boolean isCompatibleMaterial(Api api, FluidState fluid, BlockState state, Material material) throws ReflectiveOperationException {
        if (!fluid.isEmpty() && (matches(api.waterPredicate(), fluid) || matches(api.lavaPredicate(), fluid)
                || matches(api.slimePredicate(), fluid))) return true;
        String name = state.getBlock().getClass().getName().toLowerCase(Locale.ROOT);
        return switch (material) {
            case WATER -> name.contains("realisticwaterblock") || (!fluid.isEmpty() && matches(api.waterPredicate(), fluid));
            case LAVA -> name.contains("realisticlavablock") || (!fluid.isEmpty() && matches(api.lavaPredicate(), fluid));
            case SLIME -> name.contains("realisticslimeblock") || (!fluid.isEmpty() && matches(api.slimePredicate(), fluid));
            case SAND -> name.contains("realisticsand");
            case LEAVES -> name.contains("realisticleaves");
            case NONE -> false;
        };
    }

    private static boolean matches(Method method, FluidState fluid) throws ReflectiveOperationException {
        return method != null && Boolean.TRUE.equals(method.invoke(null, fluid));
    }

    private static Api api() throws ReflectiveOperationException {
        Api result = cachedApi;
        if (result != null) return result;
        synchronized (WindBlowerWaterCompat.class) {
            result = cachedApi;
            if (result != null) return result;
            Class<?> worldClass = Class.forName(WORLD_CLASS);
            Object world = worldClass.getMethod("get").invoke(null);
            Method explosion = worldClass.getMethod("onExplosion", Vec3.class, float.class);
            Method water = worldClass.getMethod("isRealistic", FluidState.class);
            result = new Api(world, explosion, water, optionalPredicate(LAVA_FLUID_CLASS), optionalPredicate(SLIME_FLUID_CLASS));
            cachedApi = result;
            return result;
        }
    }

    private static Method optionalPredicate(String name) {
        try { return Class.forName(name).getMethod("isRealistic", FluidState.class); }
        catch (ReflectiveOperationException | LinkageError ignored) { return null; }
    }

    private static void spawnSpray(Level level, Vec3 center, Vec3 look, Material material) {
        int count = Math.min(16, WindBlowerConfig.effectiveParticles());
        var random = level.getRandom();
        Vec3 referenceUp = Math.abs(look.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = look.cross(referenceUp).normalize();
        Vec3 up = right.cross(look).normalize();
        ParticleOptions type = switch (material) {
            case WATER -> ParticleTypes.SPLASH;
            case LAVA -> ParticleTypes.LAVA;
            case SLIME -> ParticleTypes.ITEM_SLIME;
            case SAND -> ParticleTypes.CLOUD;
            case LEAVES, NONE -> ParticleTypes.CLOUD;
        };
        for (int i = 0; i < count; i++) {
            Vec3 position = center.add(right.scale((random.nextDouble() - 0.5) * 0.22)).add(up.scale((random.nextDouble() - 0.5) * 0.18));
            Vec3 velocity = look.scale(0.18 + random.nextDouble() * 0.30).add(right.scale((random.nextDouble() - 0.5) * 0.12));
            level.addParticle(type, position.x, position.y, position.z, velocity.x, velocity.y, velocity.z);
        }
    }
}
