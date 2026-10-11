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

	/** Esquina de la pantalla a la que se ancla el HUD del contador (la pone el arrastre en "Mover y escalar HUD"). */
	public enum HudCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

	/** Límites del desplazamiento (píxeles de la interfaz; -4 deja pegarlo al borde, por el margen) y del tamaño. */
	public static final int MIN_OFFSET = -4;
	public static final int MAX_OFFSET = 999;
	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 2.0f;

	// Gson ignora este campo al leer; solo sirve de ayuda a quien edite el archivo
	@SuppressWarnings("unused")
	private String _info = "Todo se cambia también desde el menú (tecla H o Mod Menu). recordMode: SPECIAL_ONLY o ALL. hudCorner: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT o BOTTOM_RIGHT; hudOffsetX/hudOffsetY lo separan de esa esquina (en píxeles de la interfaz) y hudScale es su tamaño (0.5 a 2). Lo más cómodo es el botón \"Mover y escalar HUD\" del menú.";

	// --- Grabación de combates contra salvajes ---
	public boolean recordingEnabled = true;
	/** SPECIAL_ONLY: shiny, legendario, mítico o ultraente. ALL: cualquier salvaje. */
	public RecordMode recordMode = RecordMode.SPECIAL_ONLY;

	// --- Contador de caza de shiny (apagado por defecto) ---
	public boolean counterEnabled = false;
	public boolean showHud = true;
	public HudCorner hudCorner = HudCorner.TOP_LEFT;
	/** Separación desde la esquina {@link #hudCorner}, en píxeles de la interfaz. */
	public int hudOffsetX = 0;
	public int hudOffsetY = 0;
	/** Tamaño del HUD: 1 = normal. */
	public float hudScale = 1.0f;
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

	/** Corrige valores imposibles (tasa menor que 1, enums desconocidos que Gson deja en null, HUD fuera de límites). */
	private HuntConfig sanitized() {
		shinyRate = Math.max(1, shinyRate);
		if (recordMode == null) recordMode = RecordMode.SPECIAL_ONLY;
		if (hudCorner == null) hudCorner = HudCorner.TOP_LEFT;
		if (lockedSpecies == null) lockedSpecies = "";
		hudOffsetX = Math.clamp(hudOffsetX, MIN_OFFSET, MAX_OFFSET);
		hudOffsetY = Math.clamp(hudOffsetY, MIN_OFFSET, MAX_OFFSET);
		hudScale = Float.isNaN(hudScale) ? 1.0f : Math.clamp(hudScale, MIN_SCALE, MAX_SCALE);
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
