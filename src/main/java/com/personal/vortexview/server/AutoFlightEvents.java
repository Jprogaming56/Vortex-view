package com.personal.vortexview.server;

import com.personal.vortexview.AitBridge;
import com.personal.vortexview.VortexConfig;
import com.personal.vortexview.VortexViewMod;
import com.personal.vortexview.net.NoticePacket;
import com.personal.vortexview.net.VortexNet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * While a player has the vortex view open, any flight event on their TARDIS finishes itself.
 * It does this by "pressing" the event's controls in order through AiT's own SequenceHandler,
 * so loyalty, sounds, particles and crash tracking all behave exactly as if a player had done it.
 * The stabiliser state does not matter.
 */
@Mod.EventBusSubscriber(modid = VortexViewMod.MOD_ID)
public final class AutoFlightEvents {
    private static final String TARDIS_MANAGER = "dev.amble.ait.core.tardis.manager.ServerTardisManager";

    /** player uuid -> tardis uuid */
    private static final Map<UUID, UUID> VIEWERS = new ConcurrentHashMap<>();
    /** tardis uuid -> ticks the current event has been waiting */
    private static final Map<UUID, Integer> WAITING = new HashMap<>();
    /** players already told about an auto-complete problem during this viewing session */
    private static final java.util.Set<UUID> REPORTED = ConcurrentHashMap.newKeySet();

    private AutoFlightEvents() {
    }

    public static void setViewing(ServerPlayer player, UUID tardis, boolean open) {
        if (open) {
            VIEWERS.put(player.getUUID(), tardis);
            REPORTED.remove(player.getUUID());
            VortexViewMod.LOGGER.info("{} opened the vortex view of TARDIS {}", player.getGameProfile().getName(), tardis);
        } else {
            VIEWERS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        VIEWERS.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || VIEWERS.isEmpty() || !VortexConfig.AUTO_COMPLETE.get()) return;
        MinecraftServer server = e.getServer();

        Iterator<Map.Entry<UUID, UUID>> it = VIEWERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            try {
                handle(server, player, entry.getValue());
            } catch (Throwable t) {
                Throwable root = t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null ? t.getCause() : t;
                if (REPORTED.add(player.getUUID())) {
                    VortexViewMod.LOGGER.error("Could not auto-complete a flight event", t);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "[Vortex View] Could not auto-complete the flight event: " + root
                                    + " (details in logs/latest.log)"));
                }
                // Give up on this event so it does not spam; the next one gets a fresh try.
                WAITING.remove(entry.getValue());
            }
        }
    }

    private static void handle(MinecraftServer server, ServerPlayer player, UUID tardisId) throws Exception {
        Class<?> managerClass = AitBridge.findClass(TARDIS_MANAGER);
        Object manager = managerClass.getMethod("getInstance").invoke(null);
        Object tardis = AitBridge.call(manager, "demandTardis", server, tardisId);
        if (tardis == null) return;

        Object sequences = AitBridge.call(tardis, "sequence");
        boolean active = (Boolean) AitBridge.call(sequences, "hasActiveSequence");
        if (!active) {
            WAITING.remove(tardisId);
            return;
        }

        int waited = WAITING.merge(tardisId, 1, Integer::sum);
        if (waited < VortexConfig.AUTO_DELAY_TICKS.get()) return;
        WAITING.remove(tardisId);

        Object sequence = AitBridge.call(sequences, "getActiveSequence");
        List<?> controls = new ArrayList<>((List<?>) AitBridge.call(sequence, "getControls"));
        VortexViewMod.LOGGER.info("Auto-completing flight event {} ({} controls) for {}",
                AitBridge.tryCall(sequence, "id"), controls.size(), player.getGameProfile().getName());

        // Press each control in order, exactly like a player at the console would.
        for (Object control : controls) {
            if (!(Boolean) AitBridge.call(sequences, "hasActiveSequence")) break;
            AitBridge.call(sequences, "add", control, player, null);
        }

        // Safety net: if it somehow did not finish, complete it directly.
        if ((Boolean) AitBridge.call(sequences, "hasActiveSequence")) {
            AitBridge.call(sequence, "execute", tardis, player);
            AitBridge.call(sequences, "setActiveSequence", null, true);
        }

        VortexNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new NoticePacket());
    }
}
