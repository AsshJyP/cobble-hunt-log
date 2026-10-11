package io.github.asshjyp.cobblehuntlog;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Botón de configuración en Mod Menu. Solo se carga si Mod Menu está instalado (entrypoint "modmenu"). */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return HuntConfigScreen::new;
	}
}
