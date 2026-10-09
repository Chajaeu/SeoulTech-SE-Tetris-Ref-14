package seoultech.se.tetris.infra.dto;

import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

/** UI ↔ Infra 간에 주고받는 설정 데이터. */
public class SettingsDTO {
    private DisplaySize displaySize = DisplaySize.MEDIUM;
    private int keyLeft = KeyEvent.VK_LEFT;
    private int keyRight = KeyEvent.VK_RIGHT;
    private int keyDown = KeyEvent.VK_DOWN;
    private int keyRotate = KeyEvent.VK_UP;
    private int keyDrop = KeyEvent.VK_SPACE;
    private int keyPause = KeyEvent.VK_P;
    private boolean colorblindMode = false;

    public SettingsDTO() {
    }

    public static SettingsDTO defaults() {
        return new SettingsDTO();
    }

    /** 잘못된/중복 키 바인딩 및 설정값을 거른다. ESC는 게임 종료 키로 예약한다(팀 합의 예정). */
    public boolean isValid() {
        if (displaySize == null) {
            return false;
        }
        int[] keys = { keyLeft, keyRight, keyDown, keyRotate, keyDrop, keyPause };
        Set<Integer> unique = new HashSet<>();
        for (int key : keys) {
            if (key <= 0 || key == KeyEvent.VK_ESCAPE || !unique.add(key)) {
                return false;
            }
        }
        return true;
    }

    public DisplaySize getDisplaySize() {
        return displaySize;
    }

    public void setDisplaySize(DisplaySize displaySize) {
        this.displaySize = displaySize;
    }

    public int getKeyLeft() {
        return keyLeft;
    }

    public void setKeyLeft(int keyLeft) {
        this.keyLeft = keyLeft;
    }

    public int getKeyRight() {
        return keyRight;
    }

    public void setKeyRight(int keyRight) {
        this.keyRight = keyRight;
    }

    public int getKeyDown() {
        return keyDown;
    }

    public void setKeyDown(int keyDown) {
        this.keyDown = keyDown;
    }

    public int getKeyRotate() {
        return keyRotate;
    }

    public void setKeyRotate(int keyRotate) {
        this.keyRotate = keyRotate;
    }

    public int getKeyDrop() {
        return keyDrop;
    }

    public void setKeyDrop(int keyDrop) {
        this.keyDrop = keyDrop;
    }

    public int getKeyPause() {
        return keyPause;
    }

    public void setKeyPause(int keyPause) {
        this.keyPause = keyPause;
    }

    public boolean isColorblindMode() {
        return colorblindMode;
    }

    public void setColorblindMode(boolean colorblindMode) {
        this.colorblindMode = colorblindMode;
    }
}
