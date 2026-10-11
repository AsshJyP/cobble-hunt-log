package io.github.asshjyp.cobblehuntlog;

import com.cobblemon.mod.common.client.CobblemonClient;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Recuadro pequeño con la caza actual: especie, encuentros, tiempo y probabilidad acumulada.
 * Se ancla a una esquina con un desplazamiento y un tamaño que se eligen en {@link HudEditScreen}.
 */
public final class HuntHud {
	private static final int MARGIN = 4;
	private static final int PADDING = 4;
	private static final int LINE_GAP = 2;
	private static final int BACKGROUND = 0x80000000;
	/** Especie y números del ejemplo que se enseña al colocar el HUD si aún no hay caza. */
	private static final String SAMPLE_SPECIES = "cobblemon:magikarp";
	private static final int SAMPLE_ENCOUNTERS = 1234;
	private static final long SAMPLE_MILLIS = (1 * 3600 + 23 * 60 + 45) * 1000L;

	private HuntHud() {}

	static void init() {
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(graphics));
	}

	/** Lo que enseña el HUD: sus líneas de texto, de arriba abajo. */
	public record Content(List<Component> lines) {}

	/** Contenido de la caza actual; null si no hay ninguna. */
	public static Content current() {
		HuntCounter.Entry entry = HuntCounter.currentEntry();
		return entry == null ? null : content(HuntCounter.currentSpecies(), entry.encounters, entry.huntMillis);
	}

	/** Ejemplo para la pantalla de mover y escalar cuando no hay caza en curso. */
	public static Content sample() {
		return content(SAMPLE_SPECIES, SAMPLE_ENCOUNTERS, SAMPLE_MILLIS);
	}

	private static Content content(String speciesId, int encounters, long huntMillis) {
		int rate = CobbleHuntLog.CONFIG.shinyRate;
		double chance = HuntCounter.cumulativeChance(encounters, rate);
		return new Content(List.of(
				HuntConfigScreen.speciesName(speciesId).copy().withStyle(ChatFormatting.GOLD),
				Component.translatable("cobblehuntlog.hud.encounters", encounters),
				Component.translatable("cobblehuntlog.hud.time", formatTime(huntMillis)),
				Component.translatable("cobblehuntlog.hud.chance",
						String.format(Locale.ROOT, "%.1f", chance * 100), rate)));
	}

	/** Ancho y alto del recuadro sin escalar, en píxeles de la interfaz. */
	public static int[] size(Font font, Content content) {
		int textWidth = 0;
		for (Component line : content.lines()) {
			textWidth = Math.max(textWidth, font.width(line));
		}
		int height = content.lines().size() * (font.lineHeight + LINE_GAP) - LINE_GAP;
		return new int[]{textWidth + PADDING * 2, height + PADDING * 2};
	}

	/**
	 * Esquina superior izquierda del recuadro ya escalado (ancho w, alto h): se ancla a la esquina
	 * elegida, se separa con el desplazamiento y nunca se sale de la pantalla.
	 */
	public static int[] position(HuntConfig config, int screenWidth, int screenHeight, int w, int h) {
		int x = switch (config.hudCorner) {
			case TOP_LEFT, BOTTOM_LEFT -> MARGIN + config.hudOffsetX;
			case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - w - MARGIN - config.hudOffsetX;
		};
		int y = switch (config.hudCorner) {
			case TOP_LEFT, TOP_RIGHT -> MARGIN + config.hudOffsetY;
			case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - h - MARGIN - config.hudOffsetY;
		};
		return new int[]{Math.clamp(x, 0, Math.max(0, screenWidth - w)), Math.clamp(y, 0, Math.max(0, screenHeight - h))};
	}

	/**
	 * Al contrario que {@link #position}: guarda en el config la esquina más cercana al centro del recuadro
	 * y la separación hasta ella. Así el HUD sigue pegado a esa esquina si cambia la resolución.
	 */
	public static void anchorAt(HuntConfig config, int screenWidth, int screenHeight, int x, int y, int w, int h) {
		boolean left = x + w / 2 < screenWidth / 2;
		boolean top = y + h / 2 < screenHeight / 2;
		config.hudCorner = top
				? (left ? HuntConfig.HudCorner.TOP_LEFT : HuntConfig.HudCorner.TOP_RIGHT)
				: (left ? HuntConfig.HudCorner.BOTTOM_LEFT : HuntConfig.HudCorner.BOTTOM_RIGHT);
		config.hudOffsetX = (left ? x : screenWidth - w - x) - MARGIN;
		config.hudOffsetY = (top ? y : screenHeight - h - y) - MARGIN;
	}

	private static void render(GuiGraphics graphics) {
		Minecraft client = Minecraft.getInstance();
		HuntConfig config = CobbleHuntLog.CONFIG;
		// F1 (hideGui), F3 y cualquier pantalla abierta lo ocultan
		if (!config.counterEnabled || !config.showHud || client.screen != null
				|| client.options.hideGui || client.getDebugOverlay().showDebugScreen()) {
			return;
		}
		// En combate, la interfaz de Cobblemon ocupa las cuatro esquinas (fichas, botones y Battle Log)
		if (CobblemonClient.INSTANCE.getBattle() != null) {
			return;
		}
		Content content = current();
		if (content == null) {
			return;
		}
		int[] size = size(client.font, content);
		int w = scaled(size[0], config.hudScale);
		int h = scaled(size[1], config.hudScale);
		int[] pos = position(config, graphics.guiWidth(), graphics.guiHeight(), w, h);
		draw(graphics, client.font, content, pos[0], pos[1], config.hudScale);
	}

	public static int scaled(int length, float scale) {
		return Math.round(length * scale);
	}

	/** Dibuja el recuadro con su esquina superior izquierda en (x, y) y el tamaño multiplicado por scale. */
	public static void draw(GuiGraphics graphics, Font font, Content content, int x, int y, float scale) {
		int[] size = size(font, content);
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.fill(0, 0, size[0], size[1], BACKGROUND);
		int lineY = PADDING;
		for (Component line : content.lines()) {
			graphics.drawString(font, line, PADDING, lineY, 0xFFFFFF);
			lineY += font.lineHeight + LINE_GAP;
		}
		graphics.pose().popPose();
	}

	/** h:mm:ss (o m:ss si no llega a una hora). */
	static String formatTime(long millis) {
		long seconds = millis / 1000;
		long hours = seconds / 3600;
		long minutes = seconds / 60 % 60;
		long secs = seconds % 60;
		return hours > 0
				? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
				: String.format(Locale.ROOT, "%d:%02d", minutes, secs);
	}
}
