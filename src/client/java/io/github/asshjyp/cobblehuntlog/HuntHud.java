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

/** Recuadro pequeño con la caza actual: especie, encuentros, tiempo y probabilidad acumulada. */
public final class HuntHud {
	private static final int MARGIN = 4;
	private static final int PADDING = 4;
	private static final int LINE_GAP = 2;
	private static final int BACKGROUND = 0x80000000;

	private HuntHud() {}

	static void init() {
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(graphics));
	}

	private static void render(GuiGraphics graphics) {
		Minecraft client = Minecraft.getInstance();
		HuntConfig config = CobbleHuntLog.CONFIG;
		HuntCounter.Entry entry = HuntCounter.currentEntry();
		// F1 (hideGui) y F3 también lo ocultan
		if (!config.counterEnabled || !config.showHud || entry == null
				|| client.options.hideGui || client.getDebugOverlay().showDebugScreen()) {
			return;
		}
		// En combate, la interfaz de Cobblemon ocupa las cuatro esquinas (fichas, botones y Battle Log)
		if (CobblemonClient.INSTANCE.getBattle() != null) {
			return;
		}

		double chance = HuntCounter.cumulativeChance(entry.encounters, config.shinyRate);
		List<Component> lines = List.of(
				HuntConfigScreen.speciesName(HuntCounter.currentSpecies()).copy().withStyle(ChatFormatting.GOLD),
				Component.translatable("cobblehuntlog.hud.encounters", entry.encounters),
				Component.translatable("cobblehuntlog.hud.time", formatTime(entry.huntMillis)),
				Component.translatable("cobblehuntlog.hud.chance",
						String.format(Locale.ROOT, "%.1f", chance * 100), config.shinyRate));

		Font font = client.font;
		int textWidth = 0;
		for (Component line : lines) {
			textWidth = Math.max(textWidth, font.width(line));
		}
		int boxWidth = textWidth + PADDING * 2;
		int boxHeight = lines.size() * (font.lineHeight + LINE_GAP) - LINE_GAP + PADDING * 2;
		int screenWidth = graphics.guiWidth();
		int screenHeight = graphics.guiHeight();
		int x = switch (config.hudCorner) {
			case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
			case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - boxWidth - MARGIN;
		};
		int y = switch (config.hudCorner) {
			case TOP_LEFT, TOP_RIGHT -> MARGIN;
			case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - boxHeight - MARGIN;
		};

		graphics.fill(x, y, x + boxWidth, y + boxHeight, BACKGROUND);
		int lineY = y + PADDING;
		for (Component line : lines) {
			graphics.drawString(font, line, x + PADDING, lineY, 0xFFFFFF);
			lineY += font.lineHeight + LINE_GAP;
		}
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
