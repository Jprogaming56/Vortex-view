package com.personal.vortexview.net;

import com.personal.vortexview.server.AutoFlightEvents;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Client to server: "take off", sent by the DEMAT button inside the vortex view. */
public record DematPacket(UUID tardis) {

    public static void encode(DematPacket p, FriendlyByteBuf buf) {
        buf.writeUUID(p.tardis);
    }

    public static DematPacket decode(FriendlyByteBuf buf) {
        return new DematPacket(buf.readUUID());
    }

    public static void handle(DematPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            if (c.getSender() != null) AutoFlightEvents.requestDemat(c.getSender(), p.tardis);
        });
        c.setPacketHandled(true);
    }
}
