package io.github.asshjyp.cobblehuntlog;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lista de bonificadores de shiny (armaduras, eventos, tickets…): una caja con el % y un botón − por fila,
 * y "Añadir bonificador" al final. Las filas van en dos columnas para que 8 quepan sin scroll.
 * Los cambios se aplican al momento y se guardan al cerrar.
 */
public class HuntBonusScreen extends Screen {
	private static final int BOX_WIDTH = 56;
	private static final int SMALL = 20;
	private static final int CELL_WIDTH = BOX_WIDTH + 14 + SMALL;
	private static final int COLUMN_GAP = 20;
	private static final int ROW = 24;
	private static final int ADD_WIDTH = 130;

	private final Screen parent;
	/** Texto de cada fila tal como se escribe (puede estar vacío o a medias mientras se edita). */
	private final List<String> values = new ArrayList<>();
	/** Caja que debe recibir el foco tras reconstruir la pantalla (la fila recién añadida); -1 si ninguna. */
	private int focusRow = -1;
	private int listTop;
	private int infoY;

	public HuntBonusScreen(Screen parent) {
		super(Component.translatable("cobblehuntlog.bonuses.title"));
		this.parent = parent;
		for (double bonus : CobbleHuntLog.CONFIG.shinyBonuses) {
			values.add(format(bonus));
		}
	}

	@Override
	protected void init() {
		int rows = (HuntConfig.MAX_BONUSES + 1) / 2;
		// Descripción, filas de la lista, "Añadir", línea de tasa efectiva y "Listo"
		int contentHeight = 14 + rows * ROW + ROW + 16 + 8 + 20;
		int top = Math.max(32, (height - contentHeight) / 2 + 8);
		listTop = top + 14;
		int listLeft = width / 2 - CELL_WIDTH - COLUMN_GAP / 2;

		for (int i = 0; i < values.size(); i++) {
			int index = i;
			int x = listLeft + (i % 2) * (CELL_WIDTH + COLUMN_GAP);
			int y = listTop + (i / 2) * ROW;
			EditBox box = new EditBox(font, x, y, BOX_WIDTH, SMALL, Component.translatable("cobblehuntlog.bonuses.title"));
			box.setMaxLength(7);
			// Hasta 4 cifras y 2 decimales; se acepta coma o punto
			box.setFilter(text -> text.matches("\\d{0,4}([.,]\\d{0,2})?"));
			box.setValue(values.get(i));
			box.setResponder(text -> {
				values.set(index, text);
				apply();
			});
			addRenderableWidget(box);
			if (i == focusRow) {
				setInitialFocus(box);
			}
			addRenderableWidget(Button.builder(Component.literal("−"), button -> {
						values.remove(index);
						apply();
						focusRow = -1;
						rebuildWidgets();
					})
					.bounds(x + BOX_WIDTH + 14, y, SMALL, SMALL)
					.tooltip(Tooltip.create(Component.translatable("cobblehuntlog.bonuses.remove")))
					.build());
		}
		focusRow = -1;

		int y = listTop + rows * ROW;
		boolean full = values.size() >= HuntConfig.MAX_BONUSES;
		Tooltip help = Tooltip.create(Component.translatable("cobblehuntlog.config.bonuses.tooltip"));
		int addX = width / 2 - (ADD_WIDTH + 4 + SMALL) / 2;
		Button add = addRenderableWidget(Button.builder(Component.translatable("cobblehuntlog.config.bonuses.add"), button -> addRow())
				.bounds(addX, y, ADD_WIDTH, SMALL)
				.tooltip(help)
				.build());
		Button plus = addRenderableWidget(Button.builder(Component.literal("+"), button -> addRow())
				.bounds(addX + ADD_WIDTH + 4, y, SMALL, SMALL)
				.tooltip(help)
				.build());
		add.active = !full;
		plus.active = !full;
		infoY = y + ROW + 2;

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(width / 2 - 100, infoY + 16, 200, SMALL)
				.build());
	}

	/** Nueva fila con 10 % y el foco en su caja. */
	private void addRow() {
		if (values.size() >= HuntConfig.MAX_BONUSES) {
			return;
		}
		values.add("10");
		apply();
		focusRow = values.size() - 1;
		rebuildWidgets();
	}

	/** Pasa las filas al config. Una fila vacía o a medias cuenta como 0 % (se queda en la lista). */
	private void apply() {
		List<Double> bonuses = new ArrayList<>();
		for (String text : values) {
			bonuses.add(parse(text));
		}
		CobbleHuntLog.CONFIG.shinyBonuses = bonuses;
	}

	private static double parse(String text) {
		try {
			return text.isEmpty() ? 0 : Math.clamp(Double.parseDouble(text.replace(',', '.')), 0, HuntConfig.MAX_BONUS);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** 25.0 → "25"; 12.5 → "12.5". */
	static String format(double value) {
		return value == Math.rint(value)
				? String.valueOf((long) value)
				: String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "");
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, listTop - 30, 0xFFFFFF);
		graphics.drawCenteredString(font, Component.translatable("cobblehuntlog.bonuses.help").withStyle(ChatFormatting.GRAY),
				width / 2, listTop - 14, 0xFFFFFF);
		int listLeft = width / 2 - CELL_WIDTH - COLUMN_GAP / 2;
		for (int i = 0; i < values.size(); i++) {
			int x = listLeft + (i % 2) * (CELL_WIDTH + COLUMN_GAP);
			int y = listTop + (i / 2) * ROW;
			graphics.drawString(font, "%", x + BOX_WIDTH + 4, y + (SMALL - font.lineHeight) / 2 + 1, 0xFFFFFF);
		}
		graphics.drawCenteredString(font, effectiveLine(), width / 2, infoY, 0xFFFFFF);
	}

	/** "Tasa efectiva: 1/6068 (+35 %)" con los bonificadores actuales. */
	static Component effectiveLine() {
		HuntConfig config = CobbleHuntLog.CONFIG;
		return Component.translatable("cobblehuntlog.config.bonuses.effective",
				Math.round(config.effectiveRate()), format(config.bonusTotal())).withStyle(ChatFormatting.GOLD);
	}

	@Override
	public void removed() {
		CobbleHuntLog.CONFIG.save();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
