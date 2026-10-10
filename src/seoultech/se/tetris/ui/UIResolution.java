package seoultech.se.tetris.ui;

public class UIResolution {

    public enum Size {
        SMALL(960, 540),
        MEDIUM(1024, 576),
        DEFAULT(1280, 720);

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
    }
}