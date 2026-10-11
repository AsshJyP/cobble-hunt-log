package io.github.asshjyp.cobblehuntlog;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CobbleHuntLog implements ClientModInitializer {
	public static final String MOD_ID = "cobblehuntlog";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final HuntConfig CONFIG = HuntConfig.load();

	/** Abre el menú de opciones. H por defecto; se cambia en Opciones > Controles. */
	private static final KeyMapping OPEN_MENU = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.cobblehuntlog.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.cobblehuntlog"));
	/** Suma un encuentro a mano (para encuentros fuera de combate). Sin asignar por defecto. */
	private static final KeyMapping MANUAL_ENCOUNTER = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.cobblehuntlog.manual_encounter", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(),
			"key.categories.cobblehuntlog"));

	@Override
	public void onInitializeClient() {
		BattleWatcher.init();
		BattleWatcher.register(new WildBattleRecorder());
		HuntCounter.init();
		BattleWatcher.register(new HuntCounter());
		HuntHud.init();
		// Líneas de log para comprobar la detección en latest.log
		BattleWatcher.register(new BattleWatcher.Listener() {
			@Override
			public void onWildBattleStart(BattleWatcher.WildBattle b) {
				LOGGER.info("[Cobble Hunt Log] Empieza combate contra salvaje: {} ({}) nv. {} shiny={} etiquetas={} especial={}",
						b.speciesName(), b.speciesId(), b.level(), b.shiny(), b.labels(), b.isSpecial());
			}

			@Override
			public void onWildBattleEnd(BattleWatcher.WildBattle b) {
				LOGGER.info("[Cobble Hunt Log] Termina combate contra {} tras {} s",
						b.speciesName(), (System.currentTimeMillis() - b.startMillis()) / 1000);
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// consumeClick solo da true jugando (sin otra pantalla abierta)
			while (OPEN_MENU.consumeClick()) {
				client.setScreen(new HuntConfigScreen(client.screen));
			}
			while (MANUAL_ENCOUNTER.consumeClick()) {
				HuntCounter.addManual();
			}
		});
		LOGGER.info("Cobble Hunt Log cargado");
	}
}
