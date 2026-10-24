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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server side of the vortex view.
 *
 * 1. While a player has the vortex view open, any flight event on their TARDIS finishes itself. It
 *    "presses" the event's controls in order through AiT's own SequenceHandler, so loyalty, sounds,
 *    particles and crash tracking behave exactly as if a player had done it. The stabiliser state
 *    does not matter.
 * 2. When that TARDIS comes out of flight (starts rematerialising), the viewer is sent a snapshot of
 *    the landing site so the arrival can be shown.
 */
@Mod.EventBusSubscriber(modid = VortexViewMod.MOD_ID)
public final class AutoFlightEvents {
    private static final String TARDIS_MANAGER = "dev.amble.ait.core.tardis.manager.ServerTardisManager";

    /** player uuid -> tardis uuid */
    private static final Map<UUID, UUID> VIEWERS = new ConcurrentHashMap<>();
    /** tardis uuid -> ticks the current event has been waiting */
    private static final Map<UUID, Integer> WAITING = new HashMap<>();
    /** player uuid -> last travel state seen for the TARDIS they are watching */
    private static final Map<UUID, String> LAST_STATE = new HashMap<>();
    /** player uuid -> ticks the flight has been over without the TARDIS landing */
    private static final Map<UUID, Integer> ARRIVED = new HashMap<>();
    /** player uuid -> ticks since the TARDIS started rematerialising, until the landing snapshot is sent */
    private static final Map<UUID, Integer> PENDING_SNAPSHOT = new HashMap<>();
    /** players already told about a problem during this viewing session */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private AutoFlightEvents() {
    }

    public static void setViewing(ServerPlayer player, UUID tardis, boolean open) {
        if (open) {
            VIEWERS.put(player.getUUID(), tardis);
            REPORTED.removeIf(k -> k.startsWith(player.getUUID() + ":"));
            LAST_STATE.remove(player.getUUID());
            ARRIVED.remove(player.getUUID());
            PENDING_SNAPSHOT.remove(player.getUUID());
            VortexViewMod.LOGGER.info("{} opened the vortex view of TARDIS {}", player.getGameProfile().getName(), tardis);
        } else {
            VIEWERS.remove(player.getUUID());
            LAST_STATE.remove(player.getUUID());
            ARRIVED.remove(player.getUUID());
            PENDING_SNAPSHOT.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        VIEWERS.remove(e.getEntity().getUUID());
        LAST_STATE.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || VIEWERS.isEmpty()) return;
        MinecraftServer server = e.getServer();

        Iterator<Map.Entry<UUID, UUID>> it = VIEWERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            Object tardis;
            try {
                tardis = lookup(server, entry.getValue());
            } catch (Throwable t) {
                report(player, entry.getValue(), "finding the TARDIS", t);
                continue;
            }
            if (tardis == null) continue;

            // Each stage is separate, so a problem in one never stops the others.
            if (VortexConfig.AUTO_COMPLETE.get()) {
                try {
                    autoComplete(player, tardis, entry.getValue());
                } catch (Throwable t) {
                    report(player, entry.getValue(), "auto-completing the flight event", t);
                }
            }
            if (VortexConfig.AUTO_LAND.get()) {
                try {
                    autoLand(player, tardis);
                } catch (Throwable t) {
                    report(player, entry.getValue(), "landing the TARDIS", t);
                }
            }
            if (VortexConfig.SHOW_LANDING.get()) {
                try {
                    watchLanding(server, player, tardis);
                } catch (Throwable t) {
                    report(player, entry.getValue(), "showing the landing", t);
                }
            }
        }
    }

    private static void report(ServerPlayer player, UUID tardisId, String what, Throwable t) {
        Throwable root = t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null ? t.getCause() : t;
        if (REPORTED.add(player.getUUID() + ":" + what)) {
            VortexViewMod.LOGGER.error("Vortex view problem while " + what, t);
            player.sendSystemMessage(Component.literal(
                    "[Vortex View] Problem while " + what + ": " + root + " (details in logs/latest.log)"));
        }
        // Give up on this event so it does not spam; the next one gets a fresh try.
        WAITING.remove(tardisId);
    }

    private static Object lookup(MinecraftServer server, UUID tardisId) throws Exception {
        Class<?> managerClass = AitBridge.findClass(TARDIS_MANAGER);
        Object manager = managerClass.getMethod("getInstance").invoke(null);
        return AitBridge.call(manager, "demandTardis", server, tardisId);
    }

    // ------------------------------------------------------------------ flight events

    private static void autoComplete(ServerPlayer player, Object tardis, UUID tardisId) throws Exception {
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

    // ------------------------------------------------------------------ landing

    private static String stateName(Object travel) throws Exception {
        Object state = AitBridge.call(travel, "getState");
        return state instanceof Enum<?> en ? en.name() : String.valueOf(state);
    }

    /**
     * AiT does not land the TARDIS when the flight runs out; the pilot has to bring the throttle back
     * to zero. While the vortex view is open nobody is at the console, so do exactly that for them:
     * setting the speed to 0 makes AiT's own handler rematerialise the TARDIS.
     */
    private static void autoLand(ServerPlayer player, Object tardis) throws Exception {
        Object travel = AitBridge.call(tardis, "travel");
        UUID id = player.getUUID();

        boolean over = "FLIGHT".equals(stateName(travel))
                && ((Number) AitBridge.call(travel, "getTargetTicks")).intValue() == 0
                && !Boolean.TRUE.equals(AitBridge.tryCall(travel, "isCrashing"));
        if (!over) {
            ARRIVED.remove(id);
            return;
        }
        if (ARRIVED.merge(id, 1, Integer::sum) < VortexConfig.AUTO_LAND_DELAY_TICKS.get()) return;
        ARRIVED.remove(id);

        // Someone flying the TARDIS by hand (real flight) is in charge; leave it alone.
        Object flight = AitBridge.tryCall(tardis, "flight");
        if (flight != null && Boolean.TRUE.equals(AitBridge.tryCall(flight, "isFlying"))) return;

        VortexViewMod.LOGGER.info("Flight is over, landing the TARDIS for {}", player.getGameProfile().getName());
        AitBridge.call(travel, "speed", 0);
        if ("FLIGHT".equals(stateName(travel))) AitBridge.call(travel, "rematerialize");
    }

    private static void watchLanding(MinecraftServer server, ServerPlayer player, Object tardis) throws Exception {
        Object travel = AitBridge.call(tardis, "travel");
        String now = stateName(travel);
        String before = LAST_STATE.put(player.getUUID(), now);

        // Flight just ended: the TARDIS is now rematerialising (or already landed).
        if ("FLIGHT".equals(before) && !"FLIGHT".equals(now) && !"DEMAT".equals(now)) {
            PENDING_SNAPSHOT.put(player.getUUID(), 0);
        }

        Integer waited = PENDING_SNAPSHOT.get(player.getUUID());
        if (waited == null) return;
        PENDING_SNAPSHOT.put(player.getUUID(), waited + 1);

        // AiT works out the final landing spot a moment after the state flips to MAT, so wait until
        // the TARDIS' position has caught up with its destination before copying the surroundings.
        boolean settled = waited >= 1 && positionSettled(travel);
        if (!settled && waited < 120) return;

        PENDING_SNAPSHOT.remove(player.getUUID());
        VortexViewMod.LOGGER.info("TARDIS is materialising for {}, sending the landing scene",
                player.getGameProfile().getName());
        VortexNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), LandingSnapshot.take(server, travel));
    }

    private static boolean positionSettled(Object travel) {
        try {
            Object pos = AitBridge.call(travel, "position");
            Object dest = AitBridge.call(travel, "destination");
            return java.util.Objects.equals(AitBridge.call(pos, "getPos"), AitBridge.call(dest, "getPos"))
                    && java.util.Objects.equals(AitBridge.tryCall(pos, "getDimension"), AitBridge.tryCall(dest, "getDimension"));
        } catch (Throwable t) {
            return true;
        }
    }
}
