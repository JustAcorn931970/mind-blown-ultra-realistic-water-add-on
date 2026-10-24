package com.windblower;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.Locale;

/** Server-authoritative gusts for normal entities, falling blocks, and Ultra Realistic Water physics bodies. */
public final class WindBlowerPhysics {
    private static final String BODY_CLASS = "me.skaffy.realistic.RealisticBlockEntity";
    private static final String PHYSICS_CLASS = "me.skaffy.realistic.RealisticPhysics";
    private static volatile Method realisticPush;
    private static volatile Class<?> realisticBody;
    private static volatile boolean warnedPhysicsApi;

    private WindBlowerPhysics() {}

    public static void gust(ServerPlayer player) {
        Level level = player.level();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 origin = player.getEyePosition();
        double range = WindBlowerConfig.effectiveRange();
        double cone = WindBlowerConfig.effectiveCone();
        Vec3 end = origin.add(look.scale(range));
        AABB area = new AABB(origin, end).inflate(cone + 1.25);
        double strength = WindBlowerConfig.effectiveStrength();
        double maxSpeed = WindBlowerConfig.effectiveMaxSpeed();

        for (Entity entity : level.getEntities(player, area, candidate -> candidate.isAlive() && !candidate.isSpectator())) {
            Vec3 toEntity = entity.getBoundingBox().getCenter().subtract(origin);
            double along = toEntity.dot(look);
            if (along < 0.0 || along > range + 1.5) continue;
            double distanceSq = toEntity.lengthSqr() - along * along;
            double allowed = cone + Math.min(1.5, entity.getBbWidth() * 0.5);
            if (distanceSq > allowed * allowed) continue;

            if (isRealisticBody(entity)) {
                pushRealisticBody(entity, origin, look, maxSpeed, strength);
                continue;
            }
            double falloff = Math.max(0.12, 1.0 - along / Math.max(1.0, range));
            Vec3 impulse = look.scale(0.30 * strength * falloff)
                    .add(0.0, WindBlowerConfig.effectiveLift() * falloff, 0.0);
            Vec3 velocity = entity.getDeltaMovement().add(impulse);
            double speed = velocity.length();
            if (speed > maxSpeed) velocity = velocity.scale(maxSpeed / speed);
            entity.setDeltaMovement(velocity);
            // In Minecraft 26.3, hurtMarked was removed. The tracker syncs non-player
            // entities; the affected player also needs an explicit motion packet.
            if (entity instanceof ServerPlayer targetPlayer) {
                targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
            }
        }

        if (WindBlowerConfig.blockLaunchEnabled) launchHitBlock(player, origin, end, look, level);
    }

    private static void launchHitBlock(ServerPlayer player, Vec3 origin, Vec3 end, Vec3 look, Level level) {
        BlockHitResult hit = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        FluidState fluid = state.getFluidState();
        if (state.isAir() || !fluid.isEmpty() || level.getBlockEntity(pos) != null) return;
        if (state.getDestroySpeed(level, pos) < 0.0F || state.getCollisionShape(level, pos).isEmpty()) return;

        String className = state.getBlock().getClass().getName().toLowerCase(Locale.ROOT);
        // Never replace Ultra Realistic Water materials with vanilla falling-block entities.
        if (className.contains("realisticwater") || className.contains("realisticsand")
                || className.contains("realisticlava") || className.contains("realisticslime")
                || className.contains("realisticleaves")) return;

        FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
        Vec3 velocity = look.scale(Math.min(1.8 + WindBlowerConfig.effectiveStrength() * 0.65, WindBlowerConfig.effectiveMaxSpeed()))
                .add(0.0, 0.24 + WindBlowerConfig.effectiveLift(), 0.0);
        block.setDeltaMovement(velocity);
        // Falling-block velocity is tracked by Minecraft's entity tracker in 26.3.
    }

    private static boolean isRealisticBody(Entity entity) {
        if (!FabricLoader.getInstance().isModLoaded("realistic-water")) return false;
        try { return Class.forName(BODY_CLASS).isInstance(entity); }
        catch (ClassNotFoundException | LinkageError ignored) { return false; }
    }

    private static void pushRealisticBody(Entity entity, Vec3 origin, Vec3 look, double maxSpeed, double strength) {
        try {
            Method method = realisticPush;
            Class<?> body = realisticBody;
            if (method == null || body == null) {
                synchronized (WindBlowerPhysics.class) {
                    method = realisticPush;
                    body = realisticBody;
                    if (method == null || body == null) {
                        body = Class.forName(BODY_CLASS);
                        Class<?> physics = Class.forName(PHYSICS_CLASS);
                        method = physics.getMethod("push", body, double.class, double.class, double.class,
                                double.class, double.class, double.class, double.class, double.class, double.class);
                        realisticBody = body;
                        realisticPush = method;
                    }
                }
            }
            Vec3 point = entity.getBoundingBox().getCenter();
            double force = Math.min(18000.0, 5000.0 + strength * 3000.0);
            double target = Math.min(maxSpeed, 2.5 + strength * 1.2);
            double kick = Math.min(15000.0, 700.0 * strength);
            method.invoke(null, entity, point.x, point.y, point.z, look.x, look.y, look.z, target, force, kick);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            if (!warnedPhysicsApi) {
                warnedPhysicsApi = true;
                WindBlowerMod.LOGGER.warn("Could not call Ultra Realistic Water 1.2.3 RealisticPhysics.push; standard entity gusts remain available.", ex);
            }
        }
    }
}
