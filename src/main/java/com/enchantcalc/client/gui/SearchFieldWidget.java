package com.enchantcalc.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class SearchFieldWidget extends TextFieldWidget {
    private final Runnable onTextChanged;

    public SearchFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height, Runnable onTextChanged) {
        super(textRenderer, x, y, width, height, Text.literal("Search"));
        this.onTextChanged = onTextChanged;
        setPlaceholder(Text.literal("Search...").styled(style -> style.withColor(0x888888)));
        setMaxLength(32);
    }

    @Override
    public void write(String text) {
        super.write(text);
        if (onTextChanged != null) {
            onTextChanged.run();
        }
    }

    @Override
    public void eraseCharacters(int characterOffset) {
        super.eraseCharacters(characterOffset);
        if (onTextChanged != null) {
            onTextChanged.run();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.isFocused()) {
            super.charTyped(chr, modifiers);
            return true;
        }
        return false;
    }
}
