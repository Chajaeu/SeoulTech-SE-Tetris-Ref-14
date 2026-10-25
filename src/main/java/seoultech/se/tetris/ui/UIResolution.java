package seoultech.se.tetris.ui;

import seoultech.se.tetris.infra.dto.DisplaySize;
import seoultech.se.tetris.infra.persistence.SettingsRepository;

public class UIResolution {

    public static Size loadSavedSize() {
        return Size.fromDisplaySize(new SettingsRepository().loadSettings().getDisplaySize());
    }

    public enum Size {
        SMALL(960, 540),
        MEDIUM(1024, 576),
        LARGE(1280, 720);

        private final int width;
        private final int height;

        Size(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public DisplaySize toDisplaySize() {
            return DisplaySize.valueOf(name());
        }

        public static Size fromDisplaySize(DisplaySize displaySize) {
            return Size.valueOf(displaySize.name());
        }
    }
}
