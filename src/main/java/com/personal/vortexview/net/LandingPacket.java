package com.personal.vortexview.net;

import com.personal.vortexview.client.ClientHooks;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: the TARDIS you are watching is rematerialising. Carries a small snapshot of the
 * blocks around the landing spot (the client cannot see other dimensions), so the arrival can be drawn.
 *
 * cells are block state ids laid out x, then z, then y: size (2*radius+1)^2 * (yMax-yMin+1).
 * radius 0 means "no snapshot available".
 */
public record LandingPacket(String dimension, long dayTime, boolean raining, float rotation,
                            int radius, int yMin, int yMax, int[] cells, boolean parked) {

    /** parked = the TARDIS was already sitting there when the view was opened (so no arrival flash). */
    public static LandingPacket empty(String dimension, long dayTime, boolean raining, float rotation, boolean parked) {
        return new LandingPacket(dimension, dayTime, raining, rotation, 0, 0, -1, new int[0], parked);
    }

    public int width() {
        return radius * 2 + 1;
    }

    public int height() {
        return yMax - yMin + 1;
    }

    public int index(int x, int y, int z) {
        return ((x + radius) * width() + (z + radius)) * height() + (y - yMin);
    }

    public static void encode(LandingPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.dimension);
        buf.writeLong(p.dayTime);
        buf.writeBoolean(p.raining);
        buf.writeFloat(p.rotation);
        buf.writeVarInt(p.radius);
        buf.writeVarInt(p.yMin);
        buf.writeVarInt(p.yMax);
        buf.writeBoolean(p.parked);
        // run-length encoded: (state id, run length) pairs
        int i = 0;
        int n = p.cells.length;
        buf.writeVarInt(n);
        while (i < n) {
            int id = p.cells[i];
            int run = 1;
            while (i + run < n && p.cells[i + run] == id) run++;
            buf.writeVarInt(id + 1); // +1 so that "unknown" (-1) stays non-negative
            buf.writeVarInt(run);
            i += run;
        }
    }

    public static LandingPacket decode(FriendlyByteBuf buf) {
        String dim = buf.readUtf();
        long time = buf.readLong();
        boolean rain = buf.readBoolean();
        float rot = buf.readFloat();
        int radius = buf.readVarInt();
        int yMin = buf.readVarInt();
        int yMax = buf.readVarInt();
        boolean parked = buf.readBoolean();
        int n = buf.readVarInt();
        int[] cells = new int[n];
        int i = 0;
        while (i < n) {
            int id = buf.readVarInt() - 1;
            int run = buf.readVarInt();
            for (int k = 0; k < run && i < n; k++) cells[i++] = id;
        }
        return new LandingPacket(dim, time, rain, rot, radius, yMin, yMax, cells, parked);
    }

    public static void handle(LandingPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.landing(p)));
        c.setPacketHandled(true);
    }
}
