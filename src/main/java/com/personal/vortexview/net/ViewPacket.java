package com.personal.vortexview.net;

import com.personal.vortexview.server.AutoFlightEvents;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Client to server: "I opened / closed the vortex view of this TARDIS". */
public record ViewPacket(UUID tardis, boolean open) {

    public static void encode(ViewPacket p, FriendlyByteBuf buf) {
        buf.writeUUID(p.tardis);
        buf.writeBoolean(p.open);
    }

    public static ViewPacket decode(FriendlyByteBuf buf) {
        return new ViewPacket(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(ViewPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            if (c.getSender() != null) AutoFlightEvents.setViewing(c.getSender(), p.tardis, p.open);
        });
        c.setPacketHandled(true);
    }
}
