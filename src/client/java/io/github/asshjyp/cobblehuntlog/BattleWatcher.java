package io.github.asshjyp.cobblehuntlog;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Detecta cuándo empieza y termina un combate propio contra un Pokémon salvaje.
 * Cada tick compara el combate actual de Cobblemon con el del tick anterior: sin mixins,
 * así no choca con el mixin de Battle Extras. Ignora combates contra NPC, PvP y los que se ven como espectador.
 */
public final class BattleWatcher {
	/** Etiquetas de especie que cuentan como "especial" (además de shiny). */
	private static final Set<String> SPECIAL_LABELS = Set.of("legendary", "mythical", "ultra_beast");

	/** Datos del rival salvaje, tomados al empezar el combate. */
	public record WildBattle(UUID battleId, ClientBattle battle, String speciesId, String speciesName,
							 int level, boolean shiny, Set<String> labels, long startMillis) {
		/** Shiny, legendario, mítico o ultraente. */
		public boolean isSpecial() {
			return shiny || labels.stream().anyMatch(SPECIAL_LABELS::contains);
		}
	}

	public interface Listener {
		default void onWildBattleStart(WildBattle battle) {}
		default void onWildBattleEnd(WildBattle battle) {}
	}

	private static final List<Listener> LISTENERS = new ArrayList<>();
	/** Último combate visto (de cualquier tipo), para no procesar el mismo dos veces. */
	private static UUID lastBattleId;
	/** Combate contra salvaje en curso, o null. */
	private static WildBattle current;
	/** Último combate contra salvaje (sigue guardado después de terminar); null si aún no hubo ninguno. */
	private static WildBattle last;

	private BattleWatcher() {}

	public static void register(Listener listener) {
		LISTENERS.add(listener);
	}

	public static WildBattle current() {
		return current;
	}

	public static WildBattle last() {
		return last;
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(BattleWatcher::tick);
	}

	private static void tick(Minecraft client) {
		// Fuera de un mundo no hay combate, aunque Cobblemon no lo haya limpiado aún
		ClientBattle battle = client.level == null ? null : CobblemonClient.INSTANCE.getBattle();
		UUID battleId = battle == null ? null : battle.getBattleId();
		if (battleId != null && battleId.equals(lastBattleId)) {
			return;
		}
		// Cambió el combate: termina el anterior (si era contra salvaje) y mira el nuevo
		lastBattleId = battleId;
		if (current != null) {
			WildBattle ended = current;
			current = null;
			LISTENERS.forEach(l -> l.onWildBattleEnd(ended));
		}
		if (battle != null && !battle.getSpectating()) {
			current = readWildBattle(battle);
			if (current != null) {
				last = current;
				WildBattle started = current;
				LISTENERS.forEach(l -> l.onWildBattleStart(started));
			}
		}
	}

	/** Lee el rival salvaje del combate; null si no es contra un salvaje o aún no tiene Pokémon activo. */
	private static WildBattle readWildBattle(ClientBattle battle) {
		ClientBattleActor wild = battle.getWildActor();
		if (wild == null) {
			return null;
		}
		for (ActiveClientBattlePokemon active : wild.getActivePokemon()) {
			ClientBattlePokemon pokemon = active.getBattlePokemon();
			if (pokemon == null) {
				continue;
			}
			Species species = pokemon.getSpecies();
			boolean shiny = Boolean.TRUE.equals(pokemon.getProperties().getShiny());
			return new WildBattle(
					battle.getBattleId(),
					battle,
					species.getResourceIdentifier().toString(),
					species.getTranslatedName().getString(),
					pokemon.getLevel(),
					shiny,
					Set.copyOf(species.getLabels()),
					System.currentTimeMillis());
		}
		return null;
	}
}
