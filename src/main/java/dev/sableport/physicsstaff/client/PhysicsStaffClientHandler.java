/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Licensed under the MIT License. See LICENSE.md in the project root.
 */

package dev.sableport.physicsstaff.client;

import dev.sableport.physicsstaff.PhysicsStaffAction;
import dev.sableport.physicsstaff.PhysicsStaffConfig;
import dev.sableport.physicsstaff.PhysicsStaffItem;
import dev.sableport.physicsstaff.network.PhysicsStaffActionPacket;
import dev.sableport.physicsstaff.network.PhysicsStaffDragPacket;
import dev.sableport.physicsstaff.network.PhysicsStaffDragSessionsPacket;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Client-side Physics Staff interaction state machine, drag controller, lock cache, and sound driver.
 */
public final class PhysicsStaffClientHandler {
    private static final PhysicsStaffClientHandler INSTANCE = new PhysicsStaffClientHandler();

    private final Map<ResourceKey<Level>, Set<UUID>> locks = new HashMap<>();
    private final Map<ResourceKey<Level>, Map<UUID, RemoteDragSession>> remoteSessions = new HashMap<>();
    private final Map<UUID, LockPulse> lockPulses = new HashMap<>();

    private @Nullable ClientDragSession dragSession;
    private @Nullable LoopingHumSoundInstance loopingSound;

    private float handExtension = 0.0f;
    private float prevHandExtension = 0.0f;
    private int lockAnimTicks = 0;

    private PhysicsStaffClientHandler() {}

    public static PhysicsStaffClientHandler get() {
        return INSTANCE;
    }

    public boolean isDragging() {
        return this.dragSession != null;
    }

    public @Nullable ClientDragSession getDragSession() {
        return this.dragSession;
    }

    public float getHandExtension(final float partialTick) {
        return Mth.lerp(partialTick, this.prevHandExtension, this.handExtension);
    }

    public float getLockFlashProgress() {
        return this.lockAnimTicks > 0 ? this.lockAnimTicks / 8.0f : 0.0f;
    }

    public boolean isLocked(final SubLevel subLevel) {
        final Set<UUID> dimLocks = this.locks.get(subLevel.getLevel().dimension());
        return dimLocks != null && dimLocks.contains(subLevel.getUniqueId());
    }

    public Set<UUID> getLockedSubLevels(final ResourceKey<Level> dimension) {
        return this.locks.getOrDefault(dimension, Collections.emptySet());
    }

    public Map<UUID, RemoteDragSession> getRemoteSessions(final ResourceKey<Level> dimension) {
        return this.remoteSessions.getOrDefault(dimension, Collections.emptyMap());
    }

    public Map<UUID, LockPulse> getLockPulses() {
        return this.lockPulses;
    }

    public void updateLocks(final ResourceKey<Level> dimension, final Set<UUID> uuids) {
        this.locks.put(dimension, new HashSet<>(uuids));
    }

    public void updateRemoteSessions(
            final ResourceKey<Level> dimension,
            final List<PhysicsStaffDragSessionsPacket.DragEntry> entries
    ) {
        final Map<UUID, RemoteDragSession> byPlayer = new HashMap<>(entries.size());
        for (final PhysicsStaffDragSessionsPacket.DragEntry entry : entries) {
            byPlayer.put(
                    entry.playerUUID(),
                    new RemoteDragSession(entry.playerUUID(), entry.subLevelUUID(), new Vector3d(entry.plotAnchor()))
            );
        }
        this.remoteSessions.put(dimension, byPlayer);
    }

    public void onLockPulse(final ResourceKey<Level> dimension, final UUID subLevelUUID, final boolean locked) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().equals(dimension)) {
            return;
        }
        this.lockPulses.put(subLevelUUID, new LockPulse(locked, 16));
    }

    public @Nullable HoverHit raycastSubLevelBlock(final Minecraft minecraft, final float partialTick) {
        final LocalPlayer player = minecraft.player;
        final ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return null;
        }

        final double maxRange = PhysicsStaffConfig.SYNCED.maxRange.get();
        final Vec3 eyePos = player.getEyePosition(partialTick);
        final Vec3 viewVec = player.getViewVector(partialTick);
        final Vec3 endPos = eyePos.add(viewVec.scale(maxRange));

        final BlockHitResult hitResult;
        if (level instanceof final LevelPoseProviderExtension poseExtension) {
            poseExtension.sable$pushPoseSupplier(subLevel -> ((ClientSubLevel) subLevel).renderPose(partialTick));
            try {
                hitResult = level.clip(new ClipContext(
                        eyePos,
                        endPos,
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        player
                ));
            } finally {
                poseExtension.sable$popPoseSupplier();
            }
        } else {
            hitResult = level.clip(new ClipContext(
                    eyePos,
                    endPos,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    player
            ));
        }

        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(hitResult.getBlockPos());
        if (subLevel == null || subLevel.isRemoved()) {
            return null;
        }

        return new HoverHit(subLevel, hitResult, hitResult.getBlockPos());
    }

    public void onAttack() {
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || !PhysicsStaffItem.isHolding(player)) {
            return;
        }

        if (this.dragSession != null) {
            final UUID subLevelUUID = this.dragSession.subLevelUUID;
            final boolean willLock = true;
            this.stopDragging(false);
            ClientPacketDistributor.sendToServer(new PhysicsStaffActionPacket(PhysicsStaffAction.LOCK, subLevelUUID));
            this.triggerLockFeedback(minecraft, willLock);
            return;
        }

        final HoverHit hit = this.raycastSubLevelBlock(minecraft, 1.0f);
        if (hit == null) {
            return;
        }

        final boolean currentlyLocked = this.isLocked(hit.subLevel);
        ClientPacketDistributor.sendToServer(new PhysicsStaffActionPacket(
                PhysicsStaffAction.LOCK,
                hit.subLevel.getUniqueId()
        ));
        this.triggerLockFeedback(minecraft, !currentlyLocked);
    }

    public void onUse() {
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || !PhysicsStaffItem.isHolding(player)) {
            return;
        }

        if (this.dragSession != null) {
            this.stopDragging(true);
            playUiSound(minecraft, SoundEvents.BEACON_DEACTIVATE, 1.4f, 0.35f);
            return;
        }

        final HoverHit hit = this.raycastSubLevelBlock(minecraft, 1.0f);
        if (hit == null) {
            return;
        }

        this.startDragging(minecraft, player, hit);
    }

    private void startDragging(final Minecraft minecraft, final LocalPlayer player, final HoverHit hit) {
        final Vec3 eyePos = player.getEyePosition(1.0f);
        final Pose3dc pose = hit.subLevel.renderPose();

        final Vector3d localAnchor = JOMLConversion.toJOML(hit.hitResult.getLocation());
        final Vector3d worldHit = pose.transformPosition(localAnchor, new Vector3d());
        final double maxRange = PhysicsStaffConfig.SYNCED.maxRange.get();
        final double initialDist = Mth.clamp(
                worldHit.distance(eyePos.x, eyePos.y, eyePos.z),
                PhysicsStaffItem.MIN_DRAG_DISTANCE,
                maxRange
        );

        final Quaterniond initialOrientation = new Quaterniond(pose.orientation()).normalize();
        this.dragSession = new ClientDragSession(
                hit.subLevel.getUniqueId(),
                localAnchor,
                initialDist,
                initialOrientation
        );

        ClientPacketDistributor.sendToServer(new PhysicsStaffActionPacket(
                PhysicsStaffAction.START_DRAG,
                this.dragSession.subLevelUUID
        ));
        this.sendDragUpdate(player);

        this.startLoopingSound(minecraft);
        playUiSound(minecraft, SoundEvents.BEACON_ACTIVATE, 1.25f, 0.35f);
    }

    public boolean onMouseMove(final double yawDelta, final double pitchDelta) {
        if (this.dragSession == null || !PhysicsStaffKeys.ROTATE_MODE.isDown()) {
            return false;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() != null || !minecraft.mouseHandler.isMouseGrabbed()) {
            return false;
        }

        final Camera camera = minecraft.gameRenderer.mainCamera();
        final Vector3d forward = new Vector3d(camera.forwardVector());
        final Vector3d up = new Vector3d(camera.upVector()).normalize();
        final Vector3d right = forward.cross(up, new Vector3d());
        if (right.lengthSquared() < 1.0e-8) {
            right.set(1.0, 0.0, 0.0);
        } else {
            right.normalize();
        }

        final double sensitivity = PhysicsStaffConfig.CLIENT.rotateSensitivity.get();
        final double radiansPerTurnUnit = Math.toRadians(0.15) * sensitivity;
        final double yawRad = -yawDelta * radiansPerTurnUnit;
        final double pitchRad = pitchDelta * radiansPerTurnUnit;

        final Quaterniond deltaRotation = new Quaterniond()
                .rotateAxis(yawRad, up.x, up.y, up.z)
                .rotateAxis(pitchRad, right.x, right.y, right.z);

        deltaRotation.mul(this.dragSession.dragOrientation, this.dragSession.dragOrientation).normalize();
        return true;
    }

    public boolean onScroll(final double scrollDeltaY) {
        if (this.dragSession == null) {
            return false;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final double sprintMult = minecraft.options.keySprint.isDown() ? 3.0 : 1.0;
        final double step = scrollDeltaY * PhysicsStaffConfig.CLIENT.scrollSensitivity.get() * 1.5 * sprintMult;
        final double maxRange = PhysicsStaffConfig.SYNCED.maxRange.get();
        this.dragSession.distance = Mth.clamp(
                this.dragSession.distance + step,
                PhysicsStaffItem.MIN_DRAG_DISTANCE,
                maxRange
        );
        return true;
    }

    public void tick() {
        this.prevHandExtension = this.handExtension;
        final float targetExtension = this.dragSession != null ? 1.0f : 0.0f;
        this.handExtension = Mth.lerp(0.35f, this.handExtension, targetExtension);
        if (this.lockAnimTicks > 0) {
            this.lockAnimTicks--;
        }

        final Iterator<Map.Entry<UUID, LockPulse>> pulseIter = this.lockPulses.entrySet().iterator();
        while (pulseIter.hasNext()) {
            final LockPulse pulse = pulseIter.next().getValue();
            pulse.ticksRemaining--;
            if (pulse.ticksRemaining <= 0) {
                pulseIter.remove();
            }
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        final ClientLevel level = minecraft.level;

        if (this.dragSession == null) {
            this.stopLoopingSound(minecraft);
            return;
        }

        if (player == null || level == null || player.isRemoved() || !PhysicsStaffItem.isHolding(player)) {
            this.stopDragging(true);
            return;
        }

        final SubLevelContainer rawContainer = SubLevelContainer.getContainer(level);
        if (!(rawContainer instanceof final ClientSubLevelContainer container)) {
            this.stopDragging(true);
            return;
        }

        final ClientSubLevel subLevel = (ClientSubLevel) container.getSubLevel(this.dragSession.subLevelUUID);
        if (subLevel == null || subLevel.isRemoved()) {
            this.stopDragging(true);
            return;
        }

        if (PhysicsStaffKeys.SCROLL_UP.isDown()) {
            this.onScroll(0.5);
        }
        if (PhysicsStaffKeys.SCROLL_DOWN.isDown()) {
            this.onScroll(-0.5);
        }

        this.sendDragUpdate(player);
    }

    public void stopDragging(final boolean sendPacket) {
        final ClientDragSession active = this.dragSession;
        this.dragSession = null;

        final Minecraft minecraft = Minecraft.getInstance();
        this.stopLoopingSound(minecraft);

        if (sendPacket && active != null && minecraft.getConnection() != null) {
            ClientPacketDistributor.sendToServer(new PhysicsStaffActionPacket(
                    PhysicsStaffAction.STOP_DRAG,
                    active.subLevelUUID
            ));
        }
    }

    public void reset() {
        this.dragSession = null;
        this.locks.clear();
        this.remoteSessions.clear();
        this.lockPulses.clear();
        this.handExtension = 0.0f;
        this.prevHandExtension = 0.0f;
        this.lockAnimTicks = 0;
        this.stopLoopingSound(Minecraft.getInstance());
    }

    private void sendDragUpdate(final LocalPlayer player) {
        if (this.dragSession == null) {
            return;
        }
        final Vec3 viewVec = player.getViewVector(1.0f);
        final Vector3d relativeGoal = new Vector3d(viewVec.x, viewVec.y, viewVec.z)
                .mul(this.dragSession.distance);
        ClientPacketDistributor.sendToServer(new PhysicsStaffDragPacket(
                this.dragSession.subLevelUUID,
                relativeGoal,
                this.dragSession.localAnchor,
                this.dragSession.dragOrientation
        ));
    }

    private void triggerLockFeedback(final Minecraft minecraft, final boolean locking) {
        this.lockAnimTicks = 8;
        if (locking) {
            playUiSound(minecraft, SoundEvents.AMETHYST_BLOCK_CHIME, 1.35f, 0.6f);
        } else {
            playUiSound(minecraft, SoundEvents.IRON_TRAPDOOR_OPEN, 1.3f, 0.35f);
        }
    }

    private void startLoopingSound(final Minecraft minecraft) {
        this.stopLoopingSound(minecraft);
        this.loopingSound = new LoopingHumSoundInstance(SoundEvents.BEACON_AMBIENT, this);
        minecraft.getSoundManager().play(this.loopingSound);
    }

    private void stopLoopingSound(final Minecraft minecraft) {
        if (this.loopingSound != null) {
            this.loopingSound.stopImmediately();
            minecraft.getSoundManager().stop(this.loopingSound);
            this.loopingSound = null;
        }
    }

    private static void playUiSound(
            final Minecraft minecraft,
            final SoundEvent sound,
            final float pitch,
            final float volume
    ) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    public record HoverHit(ClientSubLevel subLevel, BlockHitResult hitResult, BlockPos blockPos) {}

    public static final class ClientDragSession {
        public final UUID subLevelUUID;
        public final Vector3d localAnchor;
        public final Quaterniond dragOrientation;
        public double distance;

        private ClientDragSession(
                final UUID subLevelUUID,
                final Vector3d localAnchor,
                final double distance,
                final Quaterniond dragOrientation
        ) {
            this.subLevelUUID = subLevelUUID;
            this.localAnchor = localAnchor;
            this.distance = distance;
            this.dragOrientation = dragOrientation;
        }
    }

    public record RemoteDragSession(UUID playerUUID, UUID subLevelUUID, Vector3d plotAnchor) {}

    public static final class LockPulse {
        public final boolean locked;
        public int ticksRemaining;

        private LockPulse(final boolean locked, final int ticksRemaining) {
            this.locked = locked;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private static final class LoopingHumSoundInstance extends AbstractTickableSoundInstance {
        private final PhysicsStaffClientHandler handler;

        private LoopingHumSoundInstance(final SoundEvent sound, final PhysicsStaffClientHandler handler) {
            super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.handler = handler;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.22f;
            this.pitch = 1.45f;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
        }

        private void stopImmediately() {
            this.stop();
        }

        @Override
        public void tick() {
            final LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.isRemoved() || !this.handler.isDragging() || !PhysicsStaffItem.isHolding(player)) {
                this.stop();
            }
        }
    }
}
