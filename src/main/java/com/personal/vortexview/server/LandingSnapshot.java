package com.personal.vortexview.server;

import com.personal.vortexview.AitBridge;
import com.personal.vortexview.VortexViewMod;
import com.personal.vortexview.net.LandingPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Copies the blocks around a landing spot so the client can draw the arrival. */
final class LandingSnapshot {
    private static final int RADIUS = 8;
    private static final int BELOW = 4;
    private static final int ABOVE = 7;

    private LandingSnapshot() {
    }

    @SuppressWarnings("unchecked")
    static LandingPacket take(MinecraftServer server, Object travel, boolean parked) {
        String dimension = "minecraft:overworld";
        long dayTime = 6000L;
        boolean raining = false;
        float rotation = 0f;
        try {
            Object pos = AitBridge.call(travel, "position");
            BlockPos origin = (BlockPos) AitBridge.call(pos, "getPos");
            Object rot = AitBridge.tryCall(pos, "getRotation");
            if (rot instanceof Number n) rotation = n.floatValue() * 22.5f;

            ServerLevel level = null;
            Object world = AitBridge.tryCall(pos, "getWorld");
            if (world instanceof ServerLevel l) level = l;
            if (level == null) {
                Object key = AitBridge.tryCall(pos, "getDimension");
                if (key instanceof ResourceKey<?> k) level = server.getLevel((ResourceKey<Level>) k);
            }
            if (level == null) return LandingPacket.empty(dimension, dayTime, raining, rotation, parked);

            dimension = level.dimension().location().toString();
            dayTime = level.getDayTime();
            raining = level.isRaining();

            int width = RADIUS * 2 + 1;
            int height = ABOVE + BELOW + 1;
            int[] cells = new int[width * width * height];
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            int i = 0;
            for (int x = -RADIUS; x <= RADIUS; x++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    for (int y = -BELOW; y <= ABOVE; y++) {
                        m.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                        cells[i++] = stateId(level, m);
                    }
                }
            }
            return new LandingPacket(dimension, dayTime, raining, rotation, RADIUS, -BELOW, ABOVE, cells, parked);
        } catch (Throwable t) {
            VortexViewMod.LOGGER.warn("Could not snapshot the landing site; the client will use a plain platform", t);
            return LandingPacket.empty(dimension, dayTime, raining, rotation, parked);
        }
    }

    /** -1 = unknown / not loaded, treated as air by the client. */
    private static int stateId(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return -1;
        BlockState state = level.getBlockState(pos);
        // The TARDIS itself is drawn by the client, so leave AiT's exterior block out.
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.contains("exterior")) return -1;
        return Block.BLOCK_STATE_REGISTRY.getId(state);
    }
}
