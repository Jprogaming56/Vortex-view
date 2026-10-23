package com.personal.vortexview.net;

import com.personal.vortexview.client.ClientHooks;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server to client: a flight event was just auto-completed for the TARDIS you are watching. */
public record NoticePacket() {

    public static void encode(NoticePacket p, FriendlyByteBuf buf) {
    }

    public static NoticePacket decode(FriendlyByteBuf buf) {
        return new NoticePacket();
    }

    public static void handle(NoticePacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHooks::eventAutoCompleted));
        c.setPacketHandled(true);
    }
}
