package com.heishandashu.moreswitches.client.ui;

import com.heishandashu.moreswitches.MoreSwitchesMod;
import com.heishandashu.moreswitches.client.feature.ClientFeatures;
import com.heishandashu.moreswitches.client.feature.FireworkCooldownModule;
import com.heishandashu.moreswitches.config.ModSettings;
import com.heishandashu.moreswitches.config.SwitchDefinition;
import com.heishandashu.moreswitches.config.SwitchIds;
import com.heishandashu.moreswitches.i18n.ModLang;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The one screen the mod has: a title, a language button in the title band, one row per switch,
 * and a Done button.
 *
 * <p>Everything is in GUI units, so the layout follows the player's GUI scale. The frame — title
 * band and footer — is measured first; whatever is left is the scrolling viewport. Rows keep a
 * fixed readable height, so adding a switch makes the list scroll instead of squeezing the text of
 * the switches already there:</p>
 * <ul>
 *   <li>when the rows fit, they are drawn from the top of the viewport and no scrollbar appears;</li>
 *   <li>when they do not, the wheel scrolls the list and a thin scrollbar on the right shows where
 *       in the list the player is;</li>
 *   <li>the bar is an indicator, not a drag handle: the wheel, the two arrows and a click on the
 *       track are the controls.</li>
 * </ul>
 *
 * <p>Rows are clipped to the viewport and the switch buttons move with the scroll, so a
 * half-scrolled row can never draw over the title or the Done button, and a switch that is
 * scrolled out of sight cannot be clicked.</p>
 *
 * <p>The screen does not pause the game. A settings screen that freezes the world would make the
 * firework cooldown unobservable while it is being tested.</p>
 */
public class SettingsScreen extends Screen {

    private static final int PADDING_X = 24;
    private static final int TITLE_TOP = 12;
    private static final int TITLE_LINE_STEP = 12;
    private static final int TITLE_BOTTOM_GAP = 10;
    private static final int LABEL_GAP = 8;

    /** Footer: hint line above the Done button. */
    private static final int FOOTER_HEIGHT = 44;
    private static final int DONE_HEIGHT = 20;
    private static final int DONE_WIDTH = 200;

    private static final int LANGUAGE_BUTTON_WIDTH = 108;
    private static final int LANGUAGE_BUTTON_MIN_WIDTH = 64;
    private static final int LANGUAGE_BUTTON_HEIGHT = 18;
    /** Clearance kept between the centred title and the language button. */
    private static final int TITLE_BUTTON_GAP = 14;

    /** Line pitch inside a row. */
    private static final int LINE_STEP = 11;

    /**
     * Row height: a name plus a description of up to two wrapped lines, with breathing room.
     *
     * <p>Fixed rather than derived from the window, so adding a switch changes how much the list
     * scrolls instead of changing how readable the other switches are.</p>
     */
    private static final int PREFERRED_ROW_HEIGHT = LINE_STEP * 3 + 4;

    /**
     * Row height below which a row stops being useful.
     *
     * <p>A row must show at least its name plus one line of description; anything less and the
     * switch would be labelled with a bare name, which is what a player cannot act on. When the
     * window is too short for even that, the list scrolls rather than shrinking further.</p>
     */
    private static final int MIN_ROW_HEIGHT = LINE_STEP * 2 + 4;

    /** Width of the scrollbar track. */
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int SCROLLBAR_GAP = 4;
    /** Height of the arrow buttons at the ends of the scrollbar. */
    private static final int SCROLL_ARROW_HEIGHT = 9;

    private static final int TEXT_COLOR_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_COLOR_SECONDARY = 0xFFB0B0B0;
    private static final int TEXT_COLOR_HINT = 0xFF909090;
    private static final int DIVIDER_COLOR = 0x24FFFFFF;
    private static final int SCROLLBAR_TRACK = 0x30FFFFFF;
    private static final int SCROLLBAR_THUMB = 0xFFC6C6C6;
    private static final int SCROLLBAR_ARROW = 0xFFA0A0A0;

    private final Screen parent;
    private final List<String> switchIds = new ArrayList<>();
    private final List<ToggleSwitchWidget> toggles = new ArrayList<>();

    private int rowHeight = PREFERRED_ROW_HEIGHT;
    private int viewportTop = 0;
    private int viewportHeight = 0;
    private int contentHeight = 0;
    private int titleBlockHeight = 0;
    /** Scroll position in pixels, from the top of the list. */
    private int scrollOffset;

    public SettingsScreen(Screen parent) {
        super(Component.literal(ModLang.text("moreswitches.screen.title")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.switchIds.clear();
        for (SwitchDefinition definition : SwitchIds.all()) {
            this.switchIds.add(definition.id());
        }

        boolean languageBesideTitle = languageButtonFitsBesideTitle();
        this.titleBlockHeight = measureTitleBlock(languageBesideTitle);
        layoutViewport();
        clampScroll();

        this.toggles.clear();
        int toggleX = this.width - PADDING_X - ToggleSwitchWidget.PREFERRED_WIDTH;
        // The switch shrinks with the row so it can never spill into the row below.
        int toggleHeight = Math.max(10, Math.min(ToggleSwitchWidget.PREFERRED_HEIGHT, this.rowHeight - 4));
        for (int index = 0; index < this.switchIds.size(); index++) {
            String switchId = this.switchIds.get(index);
            ToggleSwitchWidget toggle = new ToggleSwitchWidget(
                    toggleX,
                    rowScreenY(index) + (this.rowHeight - toggleHeight) / 2,
                    ToggleSwitchWidget.PREFERRED_WIDTH,
                    toggleHeight,
                    Component.literal(ModLang.switchName(switchId, false)),
                    () -> ClientFeatures.isEnabled(switchId),
                    value -> ClientFeatures.setEnabled(switchId, value));
            this.toggles.add(toggle);
            this.addRenderableWidget(toggle);
        }
        positionToggles();

        addLanguageButton(languageBesideTitle);

        int buttonY = this.height - DONE_HEIGHT - 8;
        this.addRenderableWidget(Button.builder(
                        Component.literal(ModLang.text("moreswitches.screen.done")),
                        button -> this.onClose())
                .bounds(this.width / 2 - DONE_WIDTH / 2, buttonY, DONE_WIDTH, DONE_HEIGHT)
                .build());

        MoreSwitchesMod.LOGGER.info(
                "[{}] settings screen at {}x{} GUI units: {} rows of {}px, viewport {}px, content {}px, scrolling {}",
                MoreSwitchesMod.MOD_ID, this.width, this.height, this.switchIds.size(), this.rowHeight,
                this.viewportHeight, this.contentHeight, canScroll() ? "yes" : "no");
    }

    /** Half the width of the wider of the two title lines, in GUI units. */
    private int titleHalfWidth() {
        return Math.max(this.font.width(ModLang.text("moreswitches.screen.title")),
                this.font.width(ModLang.text("moreswitches.screen.title_alt"))) / 2;
    }

    /** True when a button at the right edge still leaves clearance beside the centred title. */
    private boolean languageButtonFitsBesideTitle() {
        return languageButtonWidthBesideTitle() > 0;
    }

    /**
     * Width the language button can take beside the title, or 0 when there is no usable space.
     *
     * <p>The button shrinks before it moves: a narrow window gets a shorter button rather than a
     * button that sits on top of the title. {@link #LANGUAGE_BUTTON_MIN_WIDTH} is the point below
     * which a button stops being readable, and it is then moved under the title instead.</p>
     */
    private int languageButtonWidthBesideTitle() {
        int titleRight = this.width / 2 + titleHalfWidth() + TITLE_BUTTON_GAP;
        int available = this.width - PADDING_X - titleRight;
        if (available < LANGUAGE_BUTTON_MIN_WIDTH) {
            return 0;
        }
        return Math.min(LANGUAGE_BUTTON_WIDTH, available);
    }

    /** Height of the title band, measured from the lines actually drawn plus the button if needed. */
    private int measureTitleBlock(boolean languageBesideTitle) {
        int lines = hasSecondaryTitle() ? 2 : 1;
        int height = TITLE_TOP + lines * TITLE_LINE_STEP + TITLE_BOTTOM_GAP;
        if (!languageBesideTitle) {
            height += LANGUAGE_BUTTON_HEIGHT + 4;
        }
        return height;
    }

    /**
     * Whether a second title line is drawn.
     *
     * <p>Each language file names the other language in {@code title_alt}, so a player can
     * recognise the mod in both. When both names are identical (or the second is empty) only one
     * line is drawn and the title band shrinks by one line.</p>
     */
    private static boolean hasSecondaryTitle() {
        String title = ModLang.text("moreswitches.screen.title");
        String secondary = ModLang.text("moreswitches.screen.title_alt");
        return !secondary.isEmpty() && !secondary.equals(title);
    }

    /**
     * Reserves the frame and gives the rest to the scrolling list.
     *
     * <p>The row height is a preference, not a division of the leftovers: when the rows do not fit
     * they keep their readable height and the viewport scrolls. It only shrinks on a window too
     * short even for that, so the list stays usable instead of showing one clipped row.</p>
     */
    private void layoutViewport() {
        this.viewportTop = this.titleBlockHeight;
        this.viewportHeight = Math.max(0, this.height - this.titleBlockHeight - FOOTER_HEIGHT);

        int rowCount = Math.max(1, this.switchIds.size());
        this.rowHeight = PREFERRED_ROW_HEIGHT;
        if (this.rowHeight * rowCount > this.viewportHeight && this.viewportHeight > 0) {
            int shared = Math.max(MIN_ROW_HEIGHT, this.viewportHeight / rowCount);
            // Only accept the smaller height when it still leaves room for a description line;
            // otherwise keep the readable minimum and let the list scroll.
            this.rowHeight = shared * rowCount <= this.viewportHeight ? shared : MIN_ROW_HEIGHT;
        }
        this.contentHeight = this.rowHeight * rowCount;
    }

    /** True when the rows are taller than the viewport, i.e. a scrollbar is needed. */
    private boolean canScroll() {
        return this.contentHeight > this.viewportHeight;
    }

    /** Largest scroll position, in pixels. */
    private int maxScroll() {
        return Math.max(0, this.contentHeight - this.viewportHeight);
    }

    private void clampScroll() {
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, maxScroll()));
    }

    /** Y of a row's top edge on screen, after scrolling; may fall outside the viewport. */
    private int rowScreenY(int index) {
        return this.viewportTop + index * this.rowHeight - this.scrollOffset;
    }

    /**
     * Moves the switch buttons onto their scrolled rows and hides the ones outside the viewport.
     *
     * <p>A hidden widget must also stop being clickable, otherwise the player could hit a switch
     * belonging to a row that is currently scrolled out of sight.</p>
     */
    private void positionToggles() {
        for (int index = 0; index < this.toggles.size(); index++) {
            ToggleSwitchWidget toggle = this.toggles.get(index);
            int rowY = rowScreenY(index);
            int toggleHeight = toggle.getHeight();
            toggle.setY(rowY + (this.rowHeight - toggleHeight) / 2);

            boolean onScreen = rowY + this.rowHeight > this.viewportTop
                    && rowY < this.viewportTop + this.viewportHeight;
            toggle.visible = onScreen;
            toggle.active = onScreen;
        }
    }

    /** Scrolls by {@code pixels} (negative scrolls up) and keeps the buttons in step. */
    private void scrollBy(int pixels) {
        if (!canScroll()) {
            return;
        }
        int before = this.scrollOffset;
        this.scrollOffset += pixels;
        clampScroll();
        if (before != this.scrollOffset) {
            positionToggles();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (canScroll() && mouseY >= this.viewportTop
                && mouseY < this.viewportTop + this.viewportHeight) {
            // One wheel notch scrolls one line: fine enough to read while it moves.
            scrollBy((int) (-scrollY * LINE_STEP));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Clicks on the scrollbar: the arrows scroll one line, the track jumps to that position.
     *
     * <p>Handled before the widgets so a click that lands on the bar is never also passed to a
     * switch underneath it.</p>
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int barX = scrollbarX();
        int top = this.viewportTop + SCROLL_ARROW_HEIGHT;
        int bottom = this.viewportTop + this.viewportHeight - SCROLL_ARROW_HEIGHT;
        if (canScroll() && event.x() >= barX - SCROLLBAR_GAP
                && event.x() <= barX + SCROLLBAR_WIDTH + SCROLLBAR_GAP) {
            if (event.y() >= this.viewportTop && event.y() < this.viewportTop + SCROLL_ARROW_HEIGHT) {
                scrollBy(-LINE_STEP);
                return true;
            }
            if (event.y() >= this.viewportTop + this.viewportHeight - SCROLL_ARROW_HEIGHT
                    && event.y() < this.viewportTop + this.viewportHeight) {
                scrollBy(LINE_STEP);
                return true;
            }
            if (event.y() >= top && event.y() < bottom) {
                int span = Math.max(1, bottom - top);
                this.scrollOffset = (int) ((event.y() - top) * maxScroll() / span);
                clampScroll();
                positionToggles();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Left edge of the scrollbar track. */
    private int scrollbarX() {
        return this.width - PADDING_X + SCROLLBAR_GAP;
    }

    private void addLanguageButton(boolean besideTitle) {
        // On the title line the button may shrink to fit; under the title it gets its full width
        // because nothing is beside it.
        int width = besideTitle ? languageButtonWidthBesideTitle() : LANGUAGE_BUTTON_WIDTH;
        if (width <= 0) {
            width = LANGUAGE_BUTTON_WIDTH;
        }
        String full = ModLang.text("moreswitches.screen.language")
                .replace("%s", languageLabel(ModSettings.get().language()));
        String label = fit(full, width - 8);
        int x = this.width - PADDING_X - width;
        int y = besideTitle
                ? TITLE_TOP
                : TITLE_TOP + (hasSecondaryTitle() ? TITLE_LINE_STEP * 2 : TITLE_LINE_STEP) + 4;
        Button button = Button.builder(Component.literal(label), pressed -> cycleLanguage())
                .bounds(x, y, width, LANGUAGE_BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.literal(ModLang.text("moreswitches.screen.language.tooltip"))))
                .build();
        this.addRenderableWidget(button);
    }

    private static String languageLabel(String choice) {
        return switch (choice) {
            case "zh_cn" -> ModLang.text("moreswitches.screen.language.zh_cn");
            case "en_us" -> ModLang.text("moreswitches.screen.language.en_us");
            default -> ModLang.text("moreswitches.screen.language.auto");
        };
    }

    /**
     * Cycles 自动 → 中文 → English → 自动.
     *
     * <p>Only the mod's own text changes; the game's language is never touched, so this cannot
     * surprise a player who is happy with the game in English.</p>
     */
    private void cycleLanguage() {
        String next = switch (ModSettings.get().language()) {
            case "zh_cn" -> "en_us";
            case "en_us" -> ModSettings.LANGUAGE_AUTO;
            default -> "zh_cn";
        };
        ModSettings.get().setLanguage(next);
        MoreSwitchesMod.LOGGER.info("[{}] language set to {}", MoreSwitchesMod.MOD_ID, next);
        this.rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int centre = this.width / 2;
        String title = ModLang.text("moreswitches.screen.title");
        graphics.centeredText(this.font, Component.literal(title), centre, TITLE_TOP, TEXT_COLOR_PRIMARY);

        int dividerY = TITLE_TOP + TITLE_LINE_STEP;
        if (hasSecondaryTitle()) {
            graphics.centeredText(this.font, Component.literal(ModLang.text("moreswitches.screen.title_alt")),
                    centre, TITLE_TOP + TITLE_LINE_STEP, TEXT_COLOR_SECONDARY);
            dividerY = TITLE_TOP + TITLE_LINE_STEP * 2;
        }
        graphics.fill(centre - 140, dividerY, centre + 140, dividerY + 1, DIVIDER_COLOR);

        drawRows(graphics);
        if (canScroll()) {
            drawScrollbar(graphics);
        }

        // The hint and the Done button are two separate bands at the bottom, so they cannot collide
        // even when the window is short.
        Component hint = Component.literal(ModLang.text("moreswitches.screen.hint")
                .replace("%s", SettingsHint.keyLabel()));
        graphics.centeredText(this.font, hint, centre, this.height - DONE_HEIGHT - 8 - 12, TEXT_COLOR_HINT);
    }

    /** Draws the rows that intersect the viewport, clipped to it. */
    private void drawRows(GuiGraphicsExtractor graphics) {
        if (this.viewportHeight <= 0) {
            return;
        }
        int labelX = PADDING_X;
        int labelRight = this.width - PADDING_X - ToggleSwitchWidget.PREFERRED_WIDTH - LABEL_GAP;
        if (labelRight <= labelX) {
            return;
        }

        graphics.enableScissor(labelX, this.viewportTop, labelRight, this.viewportTop + this.viewportHeight);
        try {
            for (int index = 0; index < this.switchIds.size(); index++) {
                int rowY = rowScreenY(index);
                if (rowY + this.rowHeight <= this.viewportTop
                        || rowY >= this.viewportTop + this.viewportHeight) {
                    continue;
                }
                drawRowText(graphics, this.switchIds.get(index), labelX, rowY + rowContentOffsetY());

                if (index < this.switchIds.size() - 1) {
                    int rowDividerY = rowY + this.rowHeight - 1;
                    graphics.fill(PADDING_X, rowDividerY, this.width - PADDING_X, rowDividerY + 1, DIVIDER_COLOR);
                }
            }
        } finally {
            graphics.disableScissor();
        }
    }

    /**
     * Draws the scrollbar: a track, a thumb sized to the visible fraction, and two arrows.
     *
     * <p>Only drawn when the list actually overflows, so a short list has no chrome to explain.</p>
     */
    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        int x = scrollbarX();
        int top = this.viewportTop + SCROLL_ARROW_HEIGHT;
        int bottom = this.viewportTop + this.viewportHeight - SCROLL_ARROW_HEIGHT;
        int trackHeight = Math.max(1, bottom - top);

        graphics.fill(x, top, x + SCROLLBAR_WIDTH, bottom, SCROLLBAR_TRACK);

        int thumbHeight = Math.max(8, trackHeight * this.viewportHeight / Math.max(1, this.contentHeight));
        int travel = Math.max(0, trackHeight - thumbHeight);
        int maximum = maxScroll();
        int thumbY = top + (maximum == 0 ? 0 : travel * this.scrollOffset / maximum);
        graphics.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, SCROLLBAR_THUMB);

        drawArrow(graphics, x, this.viewportTop, true);
        drawArrow(graphics, x, this.viewportTop + this.viewportHeight - SCROLL_ARROW_HEIGHT, false);
    }

    /** Draws an up or down arrow, centred on the track and {@link #SCROLL_ARROW_HEIGHT} tall. */
    private void drawArrow(GuiGraphicsExtractor graphics, int x, int y, boolean up) {
        int centre = x + SCROLLBAR_WIDTH / 2;
        int rows = SCROLL_ARROW_HEIGHT / 2 + 1;
        for (int row = 0; row < rows; row++) {
            int halfWidth = Math.min(row, SCROLLBAR_WIDTH / 2);
            int rowY = up ? y + SCROLL_ARROW_HEIGHT / 2 - row : y + SCROLL_ARROW_HEIGHT / 2 + row;
            graphics.fill(centre - halfWidth, rowY, centre + halfWidth + 1, rowY + 1, SCROLLBAR_ARROW);
        }
    }

    /** Number of text lines a row can show without touching its neighbours. */
    private int lineBudget() {
        return Math.max(1, this.rowHeight / LINE_STEP);
    }

    /** Vertically centres the text block inside the row. */
    private int rowContentOffsetY() {
        int block = Math.min(lineBudget(), 3) * LINE_STEP;
        return Math.max(1, (this.rowHeight - block) / 2);
    }

    /**
     * Draws one row's name and description.
     *
     * <p>Every line comes from the active language's file, so switching the language switches
     * every word on the screen. No line is ever taken from the other language.</p>
     *
     * <p>The name always gets its own line. The description then takes as many lines as the row
     * has room for, wrapping rather than being cut: on a short row it is shortened with an
     * ellipsis so a half-line of text never runs into the switch.</p>
     */
    private void drawRowText(GuiGraphicsExtractor graphics, String switchId, int x, int y) {
        int budget = lineBudget();
        int maxWidth = this.width - PADDING_X - ToggleSwitchWidget.PREFERRED_WIDTH - LABEL_GAP - x;
        if (maxWidth <= 0) {
            return;
        }

        String name = ModLang.switchName(switchId, false);
        String description = ModLang.switchDescription(switchId, false);
        if (SwitchIds.FIREWORK_COOLDOWN.equals(switchId)) {
            // Shows the seconds left right where the player is looking, instead of on an extra line
            // that would not fit on a short window.
            description = description + statusSuffix();
        }

        if (budget >= 3) {
            graphics.text(this.font, Component.literal(fit(name, maxWidth)), x, y, TEXT_COLOR_PRIMARY);
            List<FormattedCharSequence> descriptionLines =
                    this.font.split(Component.literal(description), maxWidth);
            int roomForDescription = Math.min(budget - 1, 2);
            for (int line = 0; line < Math.min(roomForDescription, descriptionLines.size()); line++) {
                graphics.text(this.font, descriptionLines.get(line),
                        x, y + LINE_STEP * (1 + line), TEXT_COLOR_HINT);
            }
            return;
        }
        if (budget == 2) {
            graphics.text(this.font, Component.literal(fit(name, maxWidth)), x, y, TEXT_COLOR_PRIMARY);
            graphics.text(this.font, Component.literal(fit(description, maxWidth)),
                    x, y + LINE_STEP, TEXT_COLOR_HINT);
            return;
        }
        graphics.text(this.font, Component.literal(fit(name, maxWidth)), x, y, TEXT_COLOR_PRIMARY);
    }

    /** Remaining block time as a short suffix on the firework row; empty when ready. */
    private String statusSuffix() {
        int seconds = FireworkCooldownModule.remainingSeconds();
        if (seconds <= 0) {
            return "";
        }
        return " " + ModLang.text("moreswitches.toast.firework_cooldown").replace("%s", Integer.toString(seconds));
    }

    /** Shortens {@code text} to {@code maxWidth}, ending in an ellipsis when it had to be cut. */
    private String fit(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int budget = maxWidth - this.font.width(ellipsis);
        if (budget <= 0) {
            return "";
        }
        return this.font.plainSubstrByWidth(text, budget) + ellipsis;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        // The world keeps running so the cooldown timer can be watched from this screen.
        return false;
    }
}
