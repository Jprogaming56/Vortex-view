package com.personal.vortexview.client;

import com.personal.vortexview.VortexViewMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Terminal-green button that matches the AIT monitor look. */
public class VortexButton extends AbstractWidget {
    private final Runnable onPress;
    private final float textScale;

    /** The small VORTEX button on the monitor. */
    public VortexButton(int x, int y, int width, int height, Runnable onPress) {
        this(x, y, width, height,
                Component.translatable("screen." + VortexViewMod.MOD_ID + ".button"),
                Component.translatable("screen." + VortexViewMod.MOD_ID + ".button.tooltip"),
                0.6f, onPress);
    }

    public VortexButton(int x, int y, int width, int height, Component label, Component tooltip,
                        float textScale, Runnable onPress) {
        super(x, y, width, height, label);
        this.onPress = onPress;
        this.textScale = textScale;
        if (tooltip != null) setTooltip(Tooltip.create(tooltip));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hot = isHoveredOrFocused() && active;
        int x = getX();
        int y = getY();
        int border = !active ? 0xFF3A4A44 : hot ? 0xFFB8FFE8 : 0xFF2F8F6A;
        int fill = !active ? 0xFF111A17 : hot ? 0xFF1B5E48 : 0xFF0B2A20;
        g.fill(x, y, x + width, y + height, border);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);

        var font = Minecraft.getInstance().font;
        int textColor = !active ? 0xFF6F7F78 : hot ? 0xFFFFFFFF : 0xFF55FFFF;
        g.pose().pushPose();
        g.pose().translate(x + width / 2f, y + height / 2f, 0);
        g.pose().scale(textScale, textScale, 1f);
        int w = font.width(getMessage());
        g.drawString(font, getMessage(), -w / 2, -4, textColor, false);
        g.pose().popPose();
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
