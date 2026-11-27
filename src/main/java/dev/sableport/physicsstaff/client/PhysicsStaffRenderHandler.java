/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Licensed under the MIT License. See LICENSE.md in the project root.
 */

package dev.sableport.physicsstaff.client;

import dev.sableport.physicsstaff.PhysicsStaffConfig;
import dev.sableport.physicsstaff.PhysicsStaffItem;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Map;
import java.util.UUID;

/**
 * Renders Physics Staff beams, hovered sub-level block outlines, and locked sub-level indicators using MC 26.3 Gizmos.
 */
public class PhysicsStaffRenderHandler implements DebugRenderer.SimpleDebugRenderer {
    private static final int BEAM_SEGMENTS = 18;
    private static final int COLOR_CYAN_OUTER = 0x9920B8FF;
    private static final int COLOR_CYAN_CORE = 0xFFE6FAFF;
    private static final int COLOR_GOLD_LOCK = 0xFFFF9A2E;
    private static final int COLOR_GOLD_FILL = 0x33FF9A2E;
    private static final int COLOR_HOVER_UNLOCKED = 0xFF3CE8FF;
    private static final int COLOR_HOVER_LOCKED = 0xFFFF6633;

    @Override
    public void emitGizmos(
            final double camX,
            final double camY,
            final double camZ,
            final DebugValueAccess debugValues,
            final Frustum frustum,
            final float partialTicks
    ) {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        final LocalPlayer localPlayer = minecraft.player;
        if (level == null || localPlayer == null) {
            return;
        }

        final SubLevelContainer rawContainer = SubLevelContainer.getContainer(level);
        if (!(rawContainer instanceof final ClientSubLevelContainer container)) {
            return;
        }

        final PhysicsStaffClientHandler handler = PhysicsStaffClientHandler.get();
        final double timeSeconds = (level.getGameTime() + partialTicks) * 0.05;

        this.emitActiveBeams(minecraft, level, localPlayer, container, handler, partialTicks, timeSeconds);

        if (!PhysicsStaffItem.isHolding(localPlayer)) {
            return;
        }

        if (PhysicsStaffConfig.CLIENT.renderLockIndicators.get()) {
            this.emitLockIndicators(level, container, handler, partialTicks, timeSeconds);
        }

        if (!handler.isDragging()) {
            this.emitHoverOutline(minecraft, localPlayer, handler, partialTicks);
        }
    }

    private void emitActiveBeams(
            final Minecraft minecraft,
            final ClientLevel level,
            final LocalPlayer localPlayer,
            final ClientSubLevelContainer container,
            final PhysicsStaffClientHandler handler,
            final float partialTicks,
            final double timeSeconds
    ) {
        final PhysicsStaffClientHandler.ClientDragSession localDrag = handler.getDragSession();
        if (localDrag != null) {
            final ClientSubLevel subLevel = (ClientSubLevel) container.getSubLevel(localDrag.subLevelUUID);
            if (subLevel != null && !subLevel.isRemoved()) {
                final Vec3 origin = this.getLocalStaffTipWorldPos(minecraft, localPlayer, partialTicks);
                final Vec3 eyePos = localPlayer.getEyePosition(partialTicks);
                final Vec3 viewVec = localPlayer.getViewVector(partialTicks);
                final Vec3 goalPos = eyePos.add(viewVec.scale(localDrag.distance));
                final Vector3d anchorWorld = subLevel.renderPose(partialTicks)
                        .transformPosition(localDrag.localAnchor, new Vector3d());
                final Vec3 endPos = new Vec3(anchorWorld.x, anchorWorld.y, anchorWorld.z);

                emitCurvedEnergyBeam(origin, goalPos, endPos, timeSeconds, COLOR_CYAN_OUTER, COLOR_CYAN_CORE);
            }
        }

        final Map<UUID, PhysicsStaffClientHandler.RemoteDragSession> remoteSessions =
                handler.getRemoteSessions(level.dimension());
        for (final PhysicsStaffClientHandler.RemoteDragSession session : remoteSessions.values()) {
            if (session.playerUUID().equals(localPlayer.getUUID())) {
                continue;
            }
            final Player remotePlayer = level.getPlayerByUUID(session.playerUUID());
            final ClientSubLevel subLevel = (ClientSubLevel) container.getSubLevel(session.subLevelUUID());
            if (remotePlayer == null || subLevel == null || subLevel.isRemoved()) {
                continue;
            }

            final Vec3 origin = this.getRemoteStaffTipWorldPos(remotePlayer, partialTicks);
            final Vector3d anchorWorld = subLevel.renderPose(partialTicks)
                    .transformPosition(session.plotAnchor(), new Vector3d());
            final Vec3 endPos = new Vec3(anchorWorld.x, anchorWorld.y, anchorWorld.z);
            final Vec3 midControl = origin.lerp(endPos, 0.5);

            emitCurvedEnergyBeam(origin, midControl, endPos, timeSeconds, COLOR_CYAN_OUTER, COLOR_CYAN_CORE);
        }
    }

    private Vec3 getLocalStaffTipWorldPos(
            final Minecraft minecraft,
            final LocalPlayer player,
            final float partialTicks
    ) {
        if (!minecraft.options.getCameraType().isFirstPerson()) {
            return this.getRemoteStaffTipWorldPos(player, partialTicks);
        }

        final Camera camera = minecraft.gameRenderer.mainCamera();
        final Vec3 camPos = camera.position();
        final float handSide = getStaffArmSign(player);
        final float ext = PhysicsStaffClientHandler.get().getHandExtension(partialTicks);

        final Vector3d forward = new Vector3d(camera.forwardVector());
        final Vector3d left = new Vector3d(camera.leftVector());
        final Vector3d up = new Vector3d(camera.upVector());

        final double rightOffset = handSide * Mth.lerp(ext, 0.34, 0.28);
        final double upOffset = Mth.lerp(ext, -0.18, -0.13);
        final double forwardOffset = Mth.lerp(ext, 0.55, 0.72);

        return camPos
                .add(-left.x * rightOffset + up.x * upOffset + forward.x * forwardOffset,
                        -left.y * rightOffset + up.y * upOffset + forward.y * forwardOffset,
                        -left.z * rightOffset + up.z * upOffset + forward.z * forwardOffset);
    }

    private Vec3 getRemoteStaffTipWorldPos(final Player player, final float partialTicks) {
        final Vec3 eyePos = player.getEyePosition(partialTicks);
        final Vec3 viewVec = player.getViewVector(partialTicks);
        final Vec3 rightVec = viewVec.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        final float handSide = getStaffArmSign(player);
        return eyePos
                .add(viewVec.scale(0.65))
                .add(rightVec.scale(handSide * 0.38))
                .add(0.0, -0.22, 0.0);
    }

    private static float getStaffArmSign(final Player player) {
        final boolean staffInMainHand = player.getMainHandItem().getItem() instanceof PhysicsStaffItem;
        final HumanoidArm activeArm = staffInMainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        return activeArm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
    }

    private static void emitCurvedEnergyBeam(
            final Vec3 p0,
            final Vec3 p1,
            final Vec3 p2,
            final double timeSeconds,
            final int outerColor,
            final int coreColor
    ) {
        final Vec3 beamDir = p2.subtract(p0);
        final double len = beamDir.length();
        if (len < 1.0e-4) {
            return;
        }
        final Vec3 unitDir = beamDir.scale(1.0 / len);
        final Vec3 refAxis = Math.abs(unitDir.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        final Vec3 orthoA = unitDir.cross(refAxis).normalize();
        final Vec3 orthoB = unitDir.cross(orthoA).normalize();

        Vec3 prevPos = p0;
        for (int i = 1; i <= BEAM_SEGMENTS; i++) {
            final double t = (double) i / BEAM_SEGMENTS;
            final double invT = 1.0 - t;

            // Quadratic Bezier from staff tip -> aim goal -> attached sub-level point
            final double bx = invT * invT * p0.x + 2.0 * invT * t * p1.x + t * t * p2.x;
            final double by = invT * invT * p0.y + 2.0 * invT * t * p1.y + t * t * p2.y;
            final double bz = invT * invT * p0.z + 2.0 * invT * t * p1.z + t * t * p2.z;

            final double envelope = Math.sin(t * Math.PI) * Math.min(0.18, len * 0.015);
            final double waveA = (Math.sin(t * 14.0 - timeSeconds * 16.0)
                    + 0.45 * Math.cos(t * 29.0 + timeSeconds * 23.0)) * envelope;
            final double waveB = (Math.cos(t * 11.0 - timeSeconds * 13.0)
                    + 0.45 * Math.sin(t * 23.0 + timeSeconds * 19.0)) * envelope;

            final Vec3 curPos = new Vec3(
                    bx + orthoA.x * waveA + orthoB.x * waveB,
                    by + orthoA.y * waveA + orthoB.y * waveB,
                    bz + orthoA.z * waveA + orthoB.z * waveB
            );

            Gizmos.line(prevPos, curPos, outerColor, 5.5f);
            Gizmos.line(prevPos, curPos, coreColor, 2.0f);
            prevPos = curPos;
        }

        Gizmos.cuboid(AABB.ofSize(p2, 0.18, 0.18, 0.18), GizmoStyle.strokeAndFill(coreColor, 2.0f, outerColor));
    }

    private void emitHoverOutline(
            final Minecraft minecraft,
            final LocalPlayer player,
            final PhysicsStaffClientHandler handler,
            final float partialTicks
    ) {
        final PhysicsStaffClientHandler.HoverHit hit = handler.raycastSubLevelBlock(minecraft, partialTicks);
        if (hit == null) {
            return;
        }

        final BlockState state = hit.subLevel().getLevel().getBlockState(hit.blockPos());
        final VoxelShape shape = state.getShape(hit.subLevel().getLevel(), hit.blockPos(), CollisionContext.of(player));
        if (shape.isEmpty()) {
            return;
        }

        final int color = handler.isLocked(hit.subLevel()) ? COLOR_HOVER_LOCKED : COLOR_HOVER_UNLOCKED;
        final Pose3dc pose = hit.subLevel().renderPose(partialTicks);
        final int bx = hit.blockPos().getX();
        final int by = hit.blockPos().getY();
        final int bz = hit.blockPos().getZ();

        for (final AABB box : shape.toAabbs()) {
            emitTransformedBox(pose, box.move(bx, by, bz).inflate(0.004), color, 2.5f);
        }
    }

    private void emitLockIndicators(
            final ClientLevel level,
            final ClientSubLevelContainer container,
            final PhysicsStaffClientHandler handler,
            final float partialTicks,
            final double timeSeconds
    ) {
        for (final UUID lockedUuid : handler.getLockedSubLevels(level.dimension())) {
            final ClientSubLevel subLevel = (ClientSubLevel) container.getSubLevel(lockedUuid);
            if (subLevel == null || subLevel.isRemoved()) {
                continue;
            }

            final Vector3d center = new Vector3d(subLevel.renderPose(partialTicks).position());
            final double pulseSize = 0.42 + 0.04 * Math.sin(timeSeconds * 4.0);
            final AABB markerBox = new AABB(
                    center.x - pulseSize, center.y - pulseSize, center.z - pulseSize,
                    center.x + pulseSize, center.y + pulseSize, center.z + pulseSize
            );

            Gizmos.cuboid(markerBox, GizmoStyle.strokeAndFill(COLOR_GOLD_LOCK, 2.5f, COLOR_GOLD_FILL))
                    .setAlwaysOnTop();
            Gizmos.billboardText(
                            "LOCKED",
                            new Vec3(center.x, center.y + pulseSize + 0.35, center.z),
                            TextGizmo.Style.forColorAndCentered(COLOR_GOLD_LOCK).withScale(0.28f)
                    )
                    .setAlwaysOnTop();
        }

        for (final Map.Entry<UUID, PhysicsStaffClientHandler.LockPulse> entry : handler.getLockPulses().entrySet()) {
            final ClientSubLevel subLevel = (ClientSubLevel) container.getSubLevel(entry.getKey());
            if (subLevel == null || subLevel.isRemoved()) {
                continue;
            }
            final PhysicsStaffClientHandler.LockPulse pulse = entry.getValue();
            final float alpha = Mth.clamp(pulse.ticksRemaining / 16.0f, 0.0f, 1.0f);
            final double radius = 0.65 + (1.0f - alpha) * 1.1;
            final Vector3dc pos = subLevel.renderPose(partialTicks).position();
            final int baseColor = pulse.locked ? 0xFFFF9A2E : 0xFF3CE8FF;
            final int stroke = ARGB.color((int) (alpha * 255), ARGB.red(baseColor), ARGB.green(baseColor), ARGB.blue(baseColor));
            Gizmos.cuboid(
                    AABB.ofSize(new Vec3(pos.x(), pos.y(), pos.z()), radius, radius, radius),
                    GizmoStyle.stroke(stroke, 2.5f)
            ).setAlwaysOnTop();
        }
    }

    private static void emitTransformedBox(
            final Pose3dc pose,
            final AABB box,
            final int argb,
            final float width
    ) {
        final Vec3 c000 = transformPoint(pose, box.minX, box.minY, box.minZ);
        final Vec3 c100 = transformPoint(pose, box.maxX, box.minY, box.minZ);
        final Vec3 c010 = transformPoint(pose, box.minX, box.maxY, box.minZ);
        final Vec3 c110 = transformPoint(pose, box.maxX, box.maxY, box.minZ);
        final Vec3 c001 = transformPoint(pose, box.minX, box.minY, box.maxZ);
        final Vec3 c101 = transformPoint(pose, box.maxX, box.minY, box.maxZ);
        final Vec3 c011 = transformPoint(pose, box.minX, box.maxY, box.maxZ);
        final Vec3 c111 = transformPoint(pose, box.maxX, box.maxY, box.maxZ);

        // Bottom edges
        Gizmos.line(c000, c100, argb, width);
        Gizmos.line(c100, c101, argb, width);
        Gizmos.line(c101, c001, argb, width);
        Gizmos.line(c001, c000, argb, width);

        // Top edges
        Gizmos.line(c010, c110, argb, width);
        Gizmos.line(c110, c111, argb, width);
        Gizmos.line(c111, c011, argb, width);
        Gizmos.line(c011, c010, argb, width);

        // Vertical edges
        Gizmos.line(c000, c010, argb, width);
        Gizmos.line(c100, c110, argb, width);
        Gizmos.line(c101, c111, argb, width);
        Gizmos.line(c001, c011, argb, width);
    }

    private static Vec3 transformPoint(final Pose3dc pose, final double x, final double y, final double z) {
        final Vector3d out = pose.transformPosition(new Vector3d(x, y, z));
        return new Vec3(out.x, out.y, out.z);
    }
}
