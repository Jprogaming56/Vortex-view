package com.personal.vortexview.client;

import com.personal.vortexview.VortexViewMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Small terminal-green button that matches the AIT monitor look. */
public class VortexButton extends AbstractWidget {
    private final Runnable onPress;

    public VortexButton(int x, int y, int width, int height, Runnable onPress) {
        super(x, y, width, height, Component.translatable("screen." + VortexViewMod.MOD_ID + ".button"));
        this.onPress = onPress;
        setTooltip(Tooltip.create(Component.translatable("screen." + VortexViewMod.MOD_ID + ".button.tooltip")));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hot = isHoveredOrFocused();
        int x = getX();
        int y = getY();
        int border = hot ? 0xFFB8FFE8 : 0xFF2F8F6A;
        int fill = hot ? 0xFF1B5E48 : 0xFF0B2A20;
        g.fill(x, y, x + width, y + height, border);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);

        var font = Minecraft.getInstance().font;
        float scale = 0.6f;
        int textColor = hot ? 0xFFFFFFFF : 0xFF55FFFF;
        g.pose().pushPose();
        g.pose().translate(x + width / 2f, y + height / 2f, 0);
        g.pose().scale(scale, scale, 1f);
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
