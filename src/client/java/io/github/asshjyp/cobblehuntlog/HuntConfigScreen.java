package io.github.asshjyp.cobblehuntlog;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import io.github.asshjyp.cobblehuntlog.HuntConfig.HudCorner;
import io.github.asshjyp.cobblehuntlog.HuntConfig.RecordMode;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Menú de opciones con widgets vanilla (sin Cloth Config). Se abre con la tecla H o desde Mod Menu.
 * Los cambios se aplican al momento y se guardan en el config al cerrar la pantalla.
 */
public class HuntConfigScreen extends Screen {
	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 10;
	private static final int ROW = 24;
	private static final int SECTION = 16;

	private final Screen parent;
	private Button lockButton;
	private Button resetButton;
	private boolean resetConfirm;
	// Posiciones de los títulos de sección y de la etiqueta de la tasa, calculadas en init()
	private int recordingTitleY;
	private int huntTitleY;
	private int rateLabelX;
	private int rateLabelY;

	public HuntConfigScreen(Screen parent) {
		super(Component.literal("Cobble Hunt Log"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		HuntConfig config = CobbleHuntLog.CONFIG;
		int left = width / 2 - BUTTON_WIDTH - GAP / 2;
		int right = width / 2 + GAP / 2;
		int fullWidth = BUTTON_WIDTH * 2 + GAP;
		// Alto total: 2 títulos de sección, 7 filas, separaciones y el botón "Listo"
		int contentHeight = SECTION * 2 + ROW * 7 + 6 + 8 + BUTTON_HEIGHT;
		int y = Math.max(28, (height - contentHeight) / 2 + 8);

		// --- Grabación ---
		recordingTitleY = y;
		y += SECTION;
		addRenderableWidget(CycleButton.onOffBuilder(config.recordingEnabled)
				.create(left, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("cobblehuntlog.config.recording"),
						(button, value) -> config.recordingEnabled = value));
		addRenderableWidget(CycleButton.<RecordMode>builder(mode -> Component.translatable(
						"cobblehuntlog.config.record_mode." + mode.name().toLowerCase(Locale.ROOT)))
				.withValues(RecordMode.values())
				.withInitialValue(config.recordMode)
				.withTooltip(mode -> Tooltip.create(Component.translatable(
						"cobblehuntlog.config.record_mode." + mode.name().toLowerCase(Locale.ROOT) + ".tooltip")))
				.create(right, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("cobblehuntlog.config.record_mode"),
						(button, value) -> config.recordMode = value));
		y += ROW;
		addRenderableWidget(Button.builder(Component.translatable("cobblehuntlog.config.open_folder"), button -> openFolder())
				.bounds(left, y, fullWidth, BUTTON_HEIGHT)
				.build());
		y += ROW + 6;

		// --- Caza de shiny ---
		huntTitleY = y;
		y += SECTION;
		addRenderableWidget(CycleButton.onOffBuilder(config.counterEnabled)
				.create(left, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("cobblehuntlog.config.counter"),
						(button, value) -> config.counterEnabled = value));
		addRenderableWidget(CycleButton.onOffBuilder(config.showHud)
				.create(right, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("cobblehuntlog.config.show_hud"),
						(button, value) -> config.showHud = value));
		y += ROW;
		addRenderableWidget(CycleButton.<HudCorner>builder(corner -> Component.translatable(
						"cobblehuntlog.config.hud_corner." + corner.name().toLowerCase(Locale.ROOT)))
				.withValues(HudCorner.values())
				.withInitialValue(config.hudCorner)
				.create(left, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable("cobblehuntlog.config.hud_corner"),
						(button, value) -> config.hudCorner = value));
		// "Tasa: 1 / [8192]": la etiqueta se dibuja en render(), la caja ocupa el resto de la columna
		Component rateLabel = Component.translatable("cobblehuntlog.config.shiny_rate");
		rateLabelX = right;
		rateLabelY = y + (BUTTON_HEIGHT - font.lineHeight) / 2 + 1;
		int boxX = right + font.width(rateLabel) + 6;
		EditBox rateBox = new EditBox(font, boxX, y, right + BUTTON_WIDTH - boxX, BUTTON_HEIGHT, rateLabel);
		rateBox.setMaxLength(7);
		rateBox.setFilter(text -> text.matches("\\d*"));
		rateBox.setValue(String.valueOf(config.shinyRate));
		rateBox.setTooltip(Tooltip.create(Component.translatable("cobblehuntlog.config.shiny_rate.tooltip")));
		rateBox.setResponder(text -> {
			// Vacío o 0 mientras se escribe: se mantiene la tasa anterior
			if (!text.isEmpty() && Integer.parseInt(text) >= 1) {
				config.shinyRate = Integer.parseInt(text);
			}
		});
		addRenderableWidget(rateBox);
		y += ROW;
		lockButton = addRenderableWidget(Button.builder(lockLabel(), button -> toggleLock())
				.bounds(left, y, fullWidth, BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable("cobblehuntlog.config.lock_species.tooltip")))
				.build());
		y += ROW;
		// Reiniciar pide dos clics: el primero solo cambia el texto a "¿Seguro?"
		resetConfirm = false;
		resetButton = addRenderableWidget(Button.builder(resetLabel(), button -> {
					if (HuntCounter.currentEntry() == null) {
						return;
					}
					if (resetConfirm) {
						HuntCounter.resetCurrent();
						resetConfirm = false;
					} else {
						resetConfirm = true;
					}
					button.setMessage(resetLabel());
				})
				.bounds(left, y, fullWidth, BUTTON_HEIGHT)
				.build());
		y += ROW;
		addRenderableWidget(Button.builder(Component.translatable("cobblehuntlog.config.history",
								HuntCounter.history().size()), button -> minecraft.setScreen(new HuntHistoryScreen(this)))
				.bounds(left, y, fullWidth, BUTTON_HEIGHT)
				.build());
		y += ROW + 8;

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(width / 2 - 100, y, 200, BUTTON_HEIGHT)
				.build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int left = width / 2 - BUTTON_WIDTH - GAP / 2;
		graphics.drawCenteredString(font, title, width / 2, Math.max(12, recordingTitleY - 20), 0xFFFFFF);
		graphics.drawString(font, Component.translatable("cobblehuntlog.config.section.recording")
				.withStyle(ChatFormatting.YELLOW), left, recordingTitleY + 2, 0xFFFFFF);
		graphics.drawString(font, Component.translatable("cobblehuntlog.config.section.hunt")
				.withStyle(ChatFormatting.YELLOW), left, huntTitleY + 2, 0xFFFFFF);
		graphics.drawString(font, Component.translatable("cobblehuntlog.config.shiny_rate"), rateLabelX, rateLabelY, 0xFFFFFF);
	}

	/** Fijar: toma la especie del último combate contra salvaje. Si ya hay una fijada, la suelta. */
	private void toggleLock() {
		HuntConfig config = CobbleHuntLog.CONFIG;
		if (config.lockSpecies) {
			config.lockSpecies = false;
			config.lockedSpecies = "";
		} else {
			BattleWatcher.WildBattle last = BattleWatcher.last();
			if (last == null) {
				return;
			}
			config.lockSpecies = true;
			config.lockedSpecies = last.speciesId();
		}
		lockButton.setMessage(lockLabel());
		// La especie actual cambió: el botón de reiniciar debe nombrar la nueva
		resetConfirm = false;
		resetButton.setMessage(resetLabel());
	}

	private Component resetLabel() {
		HuntCounter.Entry entry = HuntCounter.currentEntry();
		if (entry == null) {
			return Component.translatable("cobblehuntlog.config.reset.none").withStyle(ChatFormatting.GRAY);
		}
		if (resetConfirm) {
			return Component.translatable("cobblehuntlog.config.reset.confirm").withStyle(ChatFormatting.RED);
		}
		return Component.translatable("cobblehuntlog.config.reset",
				speciesName(HuntCounter.currentSpecies()), entry.encounters);
	}

	private static Component lockLabel() {
		HuntConfig config = CobbleHuntLog.CONFIG;
		Component value;
		if (config.lockSpecies && !config.lockedSpecies.isEmpty()) {
			value = speciesName(config.lockedSpecies);
		} else if (BattleWatcher.last() == null) {
			value = Component.translatable("cobblehuntlog.config.lock_species.none").withStyle(ChatFormatting.GRAY);
		} else {
			value = Component.translatable("cobblehuntlog.config.lock_species.none");
		}
		return Component.translatable("cobblehuntlog.config.lock_species", value);
	}

	/** Nombre traducido de la especie; si Cobblemon no la conoce, el id tal cual. */
	static Component speciesName(String speciesId) {
		ResourceLocation id = ResourceLocation.tryParse(speciesId);
		Species species = id == null ? null : PokemonSpecies.getByIdentifier(id);
		return species == null ? Component.literal(speciesId) : species.getTranslatedName();
	}

	private static void openFolder() {
		try {
			Files.createDirectories(WildBattleRecorder.DIR);
		} catch (IOException e) {
			CobbleHuntLog.LOGGER.warn("No se pudo crear {}", WildBattleRecorder.DIR, e);
		}
		Util.getPlatform().openPath(WildBattleRecorder.DIR);
	}

	@Override
	public void removed() {
		// Se llama al cerrar con Esc, con "Listo" o si otra pantalla la reemplaza
		CobbleHuntLog.CONFIG.save();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
