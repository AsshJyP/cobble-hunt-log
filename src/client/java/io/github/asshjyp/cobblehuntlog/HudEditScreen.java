package io.github.asshjyp.cobblehuntlog;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Mover y escalar el HUD: se arrastra con el ratón, la rueda cambia el tamaño y las flechas lo afinan
 * píxel a píxel (Mayús = 10). Sin fondo desenfocado, para ver el juego y los demás HUD mientras se coloca.
 * Al soltarlo se ancla a la esquina más cercana ({@link HuntHud#anchorAt}).
 * Mismo sistema que la pantalla homónima de Cobble EV Tracker.
 */
public class HudEditScreen extends Screen {
	private static final int BUTTON_WIDTH = 98;
	private static final int BUTTON_HEIGHT = 20;
	private static final float SCALE_STEP = 0.1f;
	private static final int OUTLINE = 0xFFFFFFFF;
	private static final int OUTLINE_IDLE = 0xA0FFFFFF;

	private final Screen parent;
	private boolean dragging;
	private double grabX;
	private double grabY;

	public HudEditScreen(Screen parent) {
		super(Component.translatable("cobblehuntlog.edit.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int y = height / 2 + 16;
		addRenderableWidget(Button.builder(Component.translatable("cobblehuntlog.edit.reset"), button -> {
					HuntConfig config = CobbleHuntLog.CONFIG;
					config.hudCorner = HuntConfig.HudCorner.TOP_LEFT;
					config.hudOffsetX = 0;
					config.hudOffsetY = 0;
					config.hudScale = 1.0f;
				})
				.bounds(width / 2 - BUTTON_WIDTH - 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(width / 2 + 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());
	}

	/** La caza actual; un ejemplo si aún no hay ninguna. */
	private HuntHud.Content content() {
		HuntHud.Content content = HuntHud.current();
		return content == null ? HuntHud.sample() : content;
	}

	/** x, y, ancho y alto del HUD en pantalla, ya escalado. */
	private int[] bounds() {
		HuntConfig config = CobbleHuntLog.CONFIG;
		int[] size = HuntHud.size(font, content());
		int w = HuntHud.scaled(size[0], config.hudScale);
		int h = HuntHud.scaled(size[1], config.hudScale);
		int[] pos = HuntHud.position(config, width, height, w, h);
		return new int[]{pos[0], pos[1], w, h};
	}

	private boolean isOver(int[] box, double mouseX, double mouseY) {
		return mouseX >= box[0] && mouseX < box[0] + box[2] && mouseY >= box[1] && mouseY < box[1] + box[3];
	}

	/** Coloca la esquina superior izquierda del HUD en (x, y), sin salir de la pantalla. */
	private void moveTo(int x, int y, int w, int h) {
		x = Math.clamp(x, 0, Math.max(0, width - w));
		y = Math.clamp(y, 0, Math.max(0, height - h));
		HuntHud.anchorAt(CobbleHuntLog.CONFIG, width, height, x, y, w, h);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// Sin desenfoque ni oscurecer: se ve el juego (y los demás HUD) detrás
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		HuntConfig config = CobbleHuntLog.CONFIG;
		int[] box = bounds();
		HuntHud.draw(graphics, font, content(), box[0], box[1], config.hudScale);
		graphics.renderOutline(box[0] - 1, box[1] - 1, box[2] + 2, box[3] + 2,
				dragging || isOver(box, mouseX, mouseY) ? OUTLINE : OUTLINE_IDLE);
		super.render(graphics, mouseX, mouseY, partialTick);

		int textY = height / 2 - 24;
		graphics.drawCenteredString(font, title, width / 2, textY, 0xFFFF55);
		graphics.drawCenteredString(font, Component.translatable("cobblehuntlog.edit.help"), width / 2, textY + 12, 0xFFFFFF);
		graphics.drawCenteredString(font, Component.translatable("cobblehuntlog.edit.scale",
				String.format(Locale.ROOT, "%.1f", config.hudScale)), width / 2, textY + 24, 0xAAAAAA);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		int[] box = bounds();
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isOver(box, mouseX, mouseY)) {
			dragging = true;
			grabX = mouseX - box[0];
			grabY = mouseY - box[1];
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (dragging) {
			int[] box = bounds();
			moveTo((int) Math.round(mouseX - grabX), (int) Math.round(mouseY - grabY), box[2], box[3]);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		dragging = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY == 0) {
			return false;
		}
		HuntConfig config = CobbleHuntLog.CONFIG;
		float scale = config.hudScale + (scrollY > 0 ? SCALE_STEP : -SCALE_STEP);
		// Redondeo a décimas para que no se acumulen errores (0.30000001…)
		config.hudScale = Math.clamp(Math.round(scale * 10) / 10f, HuntConfig.MIN_SCALE, HuntConfig.MAX_SCALE);
		// Al cambiar de tamaño crece desde su esquina; se recoloca por si ya no cabe
		int[] box = bounds();
		moveTo(box[0], box[1], box[2], box[3]);
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
		int dx = switch (keyCode) {
			case GLFW.GLFW_KEY_LEFT -> -step;
			case GLFW.GLFW_KEY_RIGHT -> step;
			default -> 0;
		};
		int dy = switch (keyCode) {
			case GLFW.GLFW_KEY_UP -> -step;
			case GLFW.GLFW_KEY_DOWN -> step;
			default -> 0;
		};
		if (dx != 0 || dy != 0) {
			int[] box = bounds();
			moveTo(box[0] + dx, box[1] + dy, box[2], box[3]);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
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
