package com.enchantcalc.gui;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Mouse buttons by meaning. The raw numbers differ between versions: GLFW (up to 26.2) has
 * left = 0 and right = 1, SDL (26.3 onward) has left = 1 and right = 3.
 */
enum MouseButton {
    LEFT, RIGHT, MIDDLE, OTHER;

    static MouseButton of(int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            return LEFT;
        }
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            return RIGHT;
        }
        if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
            return MIDDLE;
        }
        return OTHER;
    }
}
