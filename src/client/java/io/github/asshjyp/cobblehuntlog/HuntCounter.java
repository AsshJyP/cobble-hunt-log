package io.github.asshjyp.cobblehuntlog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.asshjyp.cobblehuntlog.BattleWatcher.WildBattle;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contador de caza de shiny: encuentros y tiempo por especie, en {@code config/cobblehuntlog/hunts.json}.
 * Solo cuenta con el contador activo; con "especie fija", solo esa especie.
 */
public final class HuntCounter implements BattleWatcher.Listener {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = HuntConfig.DIR.resolve("hunts.json");
	/** Cada cuánto se guarda el tiempo acumulado (además de al salir). */
	private static final long AUTOSAVE_MILLIS = 60_000;
	private static final DateTimeFormatter HISTORY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	/** Toast propio de 8 s (el id agrupa toasts: uno nuevo reemplaza al anterior del mismo id). */
	private static final SystemToast.SystemToastId SHINY_TOAST = new SystemToast.SystemToastId(8000);

	/** Lo que se guarda en hunts.json. */
	static final class Data {
		/** Especie que muestra el HUD (la del último encuentro contado). */
		String current = "";
		Map<String, Entry> species = new LinkedHashMap<>();
		/** Shinies encontrados, del más antiguo al más reciente. */
		List<ShinyRecord> history = new ArrayList<>();
	}

	public static final class Entry {
		public int encounters;
		public long huntMillis;
	}

	public record ShinyRecord(String species, int encounters, long huntMillis, String date) {}

	private static Data data = new Data();
	private static long lastTickMillis;
	private static long lastSaveMillis;
	private static boolean dirty;

	static void init() {
		data = load();
		ClientTickEvents.END_CLIENT_TICK.register(HuntCounter::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> saveIfDirty());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveIfDirty());
	}

	@Override
	public void onWildBattleStart(WildBattle battle) {
		if (!CobbleHuntLog.CONFIG.counterEnabled || !counts(battle.speciesId())) {
			return;
		}
		data.current = battle.speciesId();
		Entry entry = entry(battle.speciesId());
		entry.encounters++;
		if (battle.shiny()) {
			onShinyFound(battle.speciesId(), entry);
		}
		save();
	}

	/** Shiny: entra en el historial, se avisa y el contador de esa especie vuelve a 0. */
	private static void onShinyFound(String speciesId, Entry entry) {
		String date = LocalDateTime.now().format(HISTORY_DATE);
		data.history.add(new ShinyRecord(speciesId, entry.encounters, entry.huntMillis, date));
		data.species.remove(speciesId);
		CobbleHuntLog.LOGGER.info("[Cobble Hunt Log] ¡Shiny {} tras {} encuentros!", speciesId, entry.encounters);

		Minecraft client = Minecraft.getInstance();
		Component species = HuntConfigScreen.speciesName(speciesId);
		SystemToast.add(client.getToasts(), SHINY_TOAST,
				Component.translatable("cobblehuntlog.shiny.toast.title", species),
				Component.translatable("cobblehuntlog.shiny.toast.message", entry.encounters,
						HuntHud.formatTime(entry.huntMillis)));
		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0f));
		if (client.player != null) {
			client.player.displayClientMessage(Component.translatable("cobblehuntlog.shiny.chat",
					species.copy().withStyle(ChatFormatting.GOLD), entry.encounters,
					HuntHud.formatTime(entry.huntMillis)).withStyle(ChatFormatting.YELLOW), false);
		}
	}

	/** Shinies encontrados, del más antiguo al más reciente. */
	public static List<ShinyRecord> history() {
		return List.copyOf(data.history);
	}

	/** Con especie fija, solo cuenta esa; si no, todas. */
	private static boolean counts(String speciesId) {
		HuntConfig config = CobbleHuntLog.CONFIG;
		return !config.lockSpecies || config.lockedSpecies.isEmpty() || config.lockedSpecies.equals(speciesId);
	}

	/** Tecla "+1 manual": suma un encuentro a la especie actual (si hay alguna). */
	static void addManual() {
		String species = currentSpecies();
		if (CobbleHuntLog.CONFIG.counterEnabled && !species.isEmpty()) {
			entry(species).encounters++;
			save();
		}
	}

	/** Pone a 0 encuentros y tiempo de la especie actual. */
	static void resetCurrent() {
		String species = currentSpecies();
		if (!species.isEmpty()) {
			data.species.remove(species);
			save();
		}
	}

	/** Especie que se está cazando: la fija si la hay, si no la del último encuentro contado. */
	public static String currentSpecies() {
		HuntConfig config = CobbleHuntLog.CONFIG;
		return config.lockSpecies && !config.lockedSpecies.isEmpty() ? config.lockedSpecies : data.current;
	}

	/** Datos de la especie actual; null si no hay ninguna. */
	public static Entry currentEntry() {
		String species = currentSpecies();
		return species.isEmpty() ? null : entry(species);
	}

	private static Entry entry(String speciesId) {
		return data.species.computeIfAbsent(speciesId, id -> new Entry());
	}

	/** Probabilidad de haber visto al menos un shiny en {@code encounters} encuentros, de 0 a 1. */
	public static double cumulativeChance(int encounters, int rate) {
		return 1 - Math.pow(1 - 1.0 / Math.max(1, rate), encounters);
	}

	private static void tick(Minecraft client) {
		long now = Util.getMillis();
		long elapsed = lastTickMillis == 0 ? 0 : now - lastTickMillis;
		lastTickMillis = now;
		// El tiempo de caza solo corre con el contador activo, dentro de un mundo y sin pausa
		Entry entry = currentEntry();
		if (entry != null && CobbleHuntLog.CONFIG.counterEnabled && client.level != null && !client.isPaused()) {
			entry.huntMillis += elapsed;
			dirty = true;
		}
		if (dirty && now - lastSaveMillis > AUTOSAVE_MILLIS) {
			save();
		}
	}

	private static void saveIfDirty() {
		if (dirty) {
			save();
		}
	}

	private static Data load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
				Data read = GSON.fromJson(reader, Data.class);
				if (read != null) {
					if (read.current == null) read.current = "";
					if (read.species == null) read.species = new LinkedHashMap<>();
					if (read.history == null) read.history = new ArrayList<>();
					return read;
				}
			} catch (IOException | JsonParseException e) {
				CobbleHuntLog.LOGGER.warn("No se pudo leer {}; se empieza un contador vacío", PATH, e);
			}
		}
		return new Data();
	}

	static void save() {
		dirty = false;
		lastSaveMillis = Util.getMillis();
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			CobbleHuntLog.LOGGER.warn("No se pudo guardar {}", PATH, e);
		}
	}
}
