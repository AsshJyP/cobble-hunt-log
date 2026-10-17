package io.github.asshjyp.cobblehuntlog;

import io.github.asshjyp.cobblehuntlog.BattleWatcher.WildBattle;
import kotlin.Unit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Graba en {@code cobble-hunt-logs/} los combates contra salvajes (todos o solo los especiales, según el config).
 * Mismo estilo que los archivos de Battle Extras, pero en una carpeta propia.
 * Los mensajes llegan por el hilo de render y se escriben en un hilo aparte para no dar tirones.
 */
public final class WildBattleRecorder implements BattleWatcher.Listener {
	public static final Path DIR = FabricLoader.getInstance().getGameDir().resolve("cobble-hunt-logs");

	private static final String DOUBLE_LINE = "═══════════════════════════════════════════════════════════";
	private static final String SINGLE_LINE = "─────────────────────────────────────────────────";
	private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	private static final DateTimeFormatter HEADER_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	/** "Turno: 3" (es_es) o "It is now turn 3" (en_us): se separa con una línea, como Battle Extras. */
	private static final Pattern TURN = Pattern.compile("^(Turno: |It is now turn )\\d+$");
	/** Puntuación con la que termina un mensaje de combate. */
	private static final Pattern END_OF_MESSAGE = Pattern.compile("[.!?)\\]…»\"]$");

	private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "CobbleHuntLog-Writer");
		thread.setDaemon(true);
		return thread;
	});

	/** Grabación en curso; solo se toca desde el hilo de render. */
	private Recording current;

	@Override
	public void onWildBattleStart(WildBattle battle) {
		HuntConfig config = CobbleHuntLog.CONFIG;
		if (!config.recordingEnabled) {
			return;
		}
		boolean special = battle.isSpecial();
		if (config.recordMode == HuntConfig.RecordMode.SPECIAL_ONLY && !special) {
			return;
		}

		String player = Minecraft.getInstance().getUser().getName();
		LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochMilli(battle.startMillis()), ZoneId.systemDefault());
		String fileName = "WILD_" + safe(player) + "Vs" + safe(battle.speciesName()) + "_" + FILE_DATE.format(start) + ".txt";
		Recording recording = new Recording(battle, DIR.resolve(fileName));
		current = recording;

		recording.write(String.join("\n",
				DOUBLE_LINE,
				"                 WILD BATTLE START",
				DOUBLE_LINE,
				"",
				"Battle Type: Wild",
				"Date: " + HEADER_DATE.format(start),
				player + " vs " + battle.speciesName(),
				"Species: " + battle.speciesName() + " (" + battle.speciesId() + ")",
				"Level: " + battle.level(),
				"Shiny: " + (battle.shiny() ? "Yes" : "No"),
				"Labels: " + (battle.labels().isEmpty() ? "-" : String.join(", ", battle.labels().stream().sorted().toList())),
				"Recorded because: " + (special ? "special" : "record all"),
				"",
				DOUBLE_LINE,
				""));

		// subscribe() reenvía primero los mensajes que ya hubiera, así que no se pierde ninguno
		battle.battle().getMessages().subscribe((FormattedCharSequence message) -> {
			recording.onMessage(toPlainText(message));
			return Unit.INSTANCE;
		});
	}

	@Override
	public void onWildBattleEnd(WildBattle battle) {
		Recording recording = current;
		if (recording == null || !recording.battle.battleId().equals(battle.battleId())) {
			return;
		}
		current = null;
		recording.flushPending();
		long seconds = (System.currentTimeMillis() - battle.startMillis()) / 1000;
		recording.write(String.join("\n",
				"",
				DOUBLE_LINE,
				"                     BATTLE END",
				DOUBLE_LINE,
				"",
				"Messages: " + recording.messages,
				"Duration: " + (seconds / 60) + " min " + (seconds % 60) + " sec",
				"",
				DOUBLE_LINE,
				""));
		recording.close();

		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.displayClientMessage(Component.translatable("cobblehuntlog.recorder.saved",
					Component.literal(recording.file.getFileName().toString()).withStyle(ChatFormatting.WHITE))
					.withStyle(ChatFormatting.GRAY), false);
		}
	}

	/** Quita los caracteres que no valen en un nombre de archivo de Windows. */
	private static String safe(String text) {
		return text.replaceAll("[^\\p{L}\\p{N}_-]", "");
	}

	/**
	 * Quita las marcas crudas del protocolo de Showdown que a veces se cuelan como mensaje (ej. "|-ohko" tras un
	 * movimiento de KO directo). Hay que hacerlo en cada trozo, antes de unirlos: "|-ohko" no termina en
	 * puntuación y se pegaría al mensaje siguiente. "|-ohko" solo → vacío; "|-ohko Texto" → "Texto".
	 */
	static String withoutProtocol(String line) {
		line = line.strip();
		if (line.startsWith("|")) {
			int space = line.indexOf(' ');
			line = space < 0 ? "" : line.substring(space + 1).strip();
		}
		return line;
	}

	private static String toPlainText(FormattedCharSequence message) {
		StringBuilder text = new StringBuilder();
		message.accept((index, style, codePoint) -> {
			text.appendCodePoint(codePoint);
			return true;
		});
		return text.toString();
	}

	/** Un archivo abierto. El escritor solo se usa desde el hilo de escritura. */
	private static final class Recording {
		final WildBattle battle;
		final Path file;
		/** Se lee desde el hilo de render: cuando es true, los mensajes que lleguen se ignoran. */
		volatile boolean closed;
		int messages;
		/** Trozos del mensaje actual que aún no termina (solo hilo de render). */
		private final StringBuilder pending = new StringBuilder();
		private BufferedWriter writer;
		private boolean failed;

		Recording(WildBattle battle, Path file) {
			this.battle = battle;
			this.file = file;
		}

		/**
		 * Cobblemon corta cada mensaje al ancho del cuadro de combate y nos llega trozo a trozo.
		 * Se juntan hasta que un trozo termina en puntuación final: los mensajes de combate son una frase completa.
		 */
		void onMessage(String line) {
			if (closed) {
				return;
			}
			line = withoutProtocol(line);
			if (line.isEmpty()) {
				return;
			}
			if (TURN.matcher(line).matches()) {
				flushPending();
				messages++;
				write("\n" + SINGLE_LINE + "\n" + line);
				return;
			}
			if (!pending.isEmpty()) {
				pending.append(' ');
			}
			pending.append(line.strip());
			if (END_OF_MESSAGE.matcher(pending).find()) {
				flushPending();
			}
		}

		/** Escribe el mensaje a medio juntar, si hay alguno (también al terminar el combate). */
		void flushPending() {
			if (!pending.isEmpty()) {
				messages++;
				write(pending.toString());
				pending.setLength(0);
			}
		}

		void write(String text) {
			WRITER.execute(() -> {
				if (failed) {
					return;
				}
				try {
					if (writer == null) {
						Files.createDirectories(file.getParent());
						writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
					}
					writer.write(text);
					writer.newLine();
					// flush por línea: si el juego se cierra de golpe, el archivo queda con lo grabado hasta ahí
					writer.flush();
				} catch (IOException e) {
					failed = true;
					CobbleHuntLog.LOGGER.warn("No se pudo escribir {}", file, e);
				}
			});
		}

		void close() {
			closed = true;
			WRITER.execute(() -> {
				try {
					if (writer != null) {
						writer.close();
					}
				} catch (IOException e) {
					CobbleHuntLog.LOGGER.warn("No se pudo cerrar {}", file, e);
				}
			});
		}
	}
}
