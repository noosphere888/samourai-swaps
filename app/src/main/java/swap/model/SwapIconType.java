package swap.model;

import javafx.scene.paint.Color;

public enum SwapIconType {
    INITIATED("EXCHANGE", Color.WHITE),
    BTC_LOCK("LOCK", Color.WHITE),
    XMR_LOCK("LOCK", Color.web("#c12727")),
    CHECK("CHECK", Color.web("#27be4d")),
    CLOSE("CLOSE", Color.web("#d42b2b"));

    private final Color color;
    private final String glyphName;

    SwapIconType(String glyphName, Color color) {
        this.glyphName = glyphName;
        this.color = color;
    }

    public Color getColor() {
        return color;
    }

    public String getGlyphName() {
        return glyphName;
    }
}
