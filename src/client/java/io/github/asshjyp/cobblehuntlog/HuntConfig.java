package io.github.asshjyp.cobblehuntlog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuración en {@code config/cobblehuntlog/config.json}. Se lee al arrancar Minecraft;
 * si no existe, se crea con los valores por defecto. El menú de opciones la cambia y la guarda.
 */
public final class HuntConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve(CobbleHuntLog.MOD_ID);
	private static final Path PATH = DIR.resolve("config.json");

	/** Qué combates contra salvajes se graban. */
	public enum RecordMode { SPECIAL_ONLY, ALL }

	/** Esquina de la pantalla donde se dibuja el HUD del contador. */
	public enum HudCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

	// Gson ignora este campo al leer; solo sirve de ayuda a quien edite el archivo
	@SuppressWarnings("unused")
	private String _info = "Todo se cambia también desde el menú (tecla H o Mod Menu). recordMode: SPECIAL_ONLY o ALL. hudCorner: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT o BOTTOM_RIGHT.";

	// --- Grabación de combates contra salvajes ---
	public boolean recordingEnabled = true;
	/** SPECIAL_ONLY: shiny, legendario, mítico o ultraente. ALL: cualquier salvaje. */
	public RecordMode recordMode = RecordMode.SPECIAL_ONLY;

	// --- Contador de caza de shiny (apagado por defecto) ---
	public boolean counterEnabled = false;
	public boolean showHud = true;
	public HudCorner hudCorner = HudCorner.TOP_LEFT;
	/** Probabilidad de shiny: 1 entre shinyRate. La de Cobblemon por defecto es 8192. */
	public int shinyRate = 8192;
	/** Si es true, solo se cuentan encuentros de {@link #lockedSpecies}. */
	public boolean lockSpecies = false;
	/** Especie fijada (ej. "cobblemon:ralts"); vacía si no hay ninguna. */
	public String lockedSpecies = "";

	public static HuntConfig load() {
		HuntConfig config = new HuntConfig();
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
				HuntConfig read = GSON.fromJson(reader, HuntConfig.class);
				if (read != null) {
					// El texto de ayuda siempre es el de esta versión, no el que hubiera en el archivo
					read._info = config._info;
					config = read;
				}
			} catch (IOException | JsonParseException e) {
				// No se sobrescribe: así no se pierde lo que se haya editado a mano
				CobbleHuntLog.LOGGER.warn("No se pudo leer {}; se usan los valores por defecto", PATH, e);
				return config.sanitized();
			}
		}
		config = config.sanitized();
		config.save();
		return config;
	}

	/** Corrige valores imposibles (tasa menor que 1, enums desconocidos que Gson deja en null). */
	private HuntConfig sanitized() {
		shinyRate = Math.max(1, shinyRate);
		if (recordMode == null) recordMode = RecordMode.SPECIAL_ONLY;
		if (hudCorner == null) hudCorner = HudCorner.TOP_LEFT;
		if (lockedSpecies == null) lockedSpecies = "";
		return this;
	}

	/** Guarda el archivo (crea los campos nuevos si faltaban). */
	public void save() {
		try {
			Files.createDirectories(DIR);
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			CobbleHuntLog.LOGGER.warn("No se pudo guardar {}", PATH, e);
		}
	}
}
