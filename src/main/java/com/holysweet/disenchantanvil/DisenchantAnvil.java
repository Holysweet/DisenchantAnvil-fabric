package com.holysweet.disenchantanvil;

import com.holysweet.disenchantanvil.config.DAConfig;
import net.fabricmc.api.ModInitializer;

public final class DisenchantAnvil implements ModInitializer {

    public static final String MOD_ID = "disenchantanvil";

    @Override
    public void onInitialize() {
        DAConfig.loadAndBake();
    }
}