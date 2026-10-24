package com.personal.vortexview.client;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * The sounds AiT plays for flight events: the alert when one starts, the chime when it is
 * completed and the thud when it is missed. They are read from the same vanilla sound fields AiT's
 * SequenceHandler uses, so they match exactly.
 */
final class EventSounds {
    private static final String[] FIELDS = {"f_11739_", "f_11871_", "f_11913_"};
    private static Set<ResourceLocation> muted;

    private EventSounds() {
    }

    static boolean isMuted(ResourceLocation id) {
        if (muted == null) {
            Set<ResourceLocation> found = new HashSet<>();
            for (String field : FIELDS) {
                try {
                    Object value = SoundEvents.class.getField(field).get(null);
                    if (value instanceof SoundEvent event) found.add(event.getLocation());
                } catch (Throwable ignored) {
                    // not an obfuscated runtime; nothing to mute
                }
            }
            muted = found;
        }
        return muted.contains(id);
    }
}
