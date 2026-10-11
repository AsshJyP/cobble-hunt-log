package io.github.asshjyp.cobblehuntlog;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Historial de shinies encontrados con el contador, del más reciente al más antiguo. */
public class HuntHistoryScreen extends Screen {
	private static final int TOP = 32;
	private static final int BOTTOM_SPACE = 36;
	private static final int ROW_HEIGHT = 24;

	private final Screen parent;

	public HuntHistoryScreen(Screen parent) {
		super(Component.translatable("cobblehuntlog.history.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		List<HuntCounter.ShinyRecord> history = HuntCounter.history();
		HistoryList list = new HistoryList(minecraft, width, height - TOP - BOTTOM_SPACE, TOP);
		for (int i = history.size() - 1; i >= 0; i--) {
			list.add(new HistoryEntry(history.get(i)));
		}
		addRenderableWidget(list);
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(width / 2 - 100, height - 28, 200, 20)
				.build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
		if (HuntCounter.history().isEmpty()) {
			graphics.drawCenteredString(font, Component.translatable("cobblehuntlog.history.empty")
					.withStyle(ChatFormatting.GRAY), width / 2, height / 2 - 10, 0xFFFFFF);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	private static final class HistoryList extends ObjectSelectionList<HistoryEntry> {
		HistoryList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, ROW_HEIGHT);
		}

		void add(HistoryEntry entry) {
			addEntry(entry);
		}

		@Override
		public int getRowWidth() {
			return 300;
		}
	}

	private static final class HistoryEntry extends ObjectSelectionList.Entry<HistoryEntry> {
		private final Component species;
		private final Component details;

		HistoryEntry(HuntCounter.ShinyRecord record) {
			species = HuntConfigScreen.speciesName(record.species()).copy().withStyle(ChatFormatting.GOLD);
			details = Component.translatable("cobblehuntlog.history.entry", record.encounters(),
					HuntHud.formatTime(record.huntMillis()), record.date()).withStyle(ChatFormatting.GRAY);
		}

		@Override
		public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
						   int mouseX, int mouseY, boolean hovering, float partialTick) {
			var font = Minecraft.getInstance().font;
			graphics.drawString(font, species, left + 4, top + 2, 0xFFFFFF);
			graphics.drawString(font, details, left + 4, top + 2 + font.lineHeight + 1, 0xFFFFFF);
		}

		@Override
		public Component getNarration() {
			return Component.empty().append(species).append(", ").append(details);
		}
	}
}
