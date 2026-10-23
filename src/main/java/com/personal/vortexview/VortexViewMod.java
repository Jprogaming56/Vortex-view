package com.personal.vortexview;

import com.personal.vortexview.net.VortexNet;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(VortexViewMod.MOD_ID)
public class VortexViewMod {
    public static final String MOD_ID = "vortexview";
    public static final Logger LOGGER = LogManager.getLogger("VortexView");

    public VortexViewMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, VortexConfig.SPEC);
        VortexNet.register();
    }
}
