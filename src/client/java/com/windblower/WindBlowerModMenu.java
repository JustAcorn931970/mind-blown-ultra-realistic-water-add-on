package com.windblower;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;

/** Adds the Wind Blower settings screen to Mod Menu when Mod Menu is installed. */
public final class WindBlowerModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return WindBlowerSettingsScreen::new;
    }
}
