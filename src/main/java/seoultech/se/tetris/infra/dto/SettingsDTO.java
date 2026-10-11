package seoultech.se.tetris.infra.dto;

import java.util.HashSet;
import java.util.Set;

// UI와 Infra 사이에서 전달하는 설정값
public class SettingsDTO {

    private DisplaySize displaySize = DisplaySize.MEDIUM;
    private Difficulty difficulty = Difficulty.NORMAL;

    // JavaFX KeyCode 이름으로 저장
    private String keyLeft = "LEFT";
    private String keyRight = "RIGHT";
    private String keyDown = "DOWN";
    private String keyRotate = "UP";
    private String keyDrop = "SPACE";
    private String keyPause = "P";

    private boolean colorblindMode = false;

    public SettingsDTO() {
    }

    public static SettingsDTO defaults() {
        return new SettingsDTO();
    }

    // 설정값이 올바른지 검사
    public boolean isValid() {

        if (displaySize == null || difficulty == null) {
            return false;
        }

        String[] keys = {
            keyLeft, keyRight, keyDown,
            keyRotate, keyDrop, keyPause
        };

        Set<String> usedKeys = new HashSet<>();

        for (String key : keys) {
            // 잘못된 키 또는 중복 키 거부
            if (!isSupportedKey(key) || !usedKeys.add(key)) {
                return false;
            }
        }

        return true;
    }

    // 사용할 수 있는 키인지 확인
    // ESCAPE는 게임 종료용으로 예약 (팀 합의 필요)
    private static boolean isSupportedKey(String key) {

        if (key == null) {
            return false;
        }

        return key.matches(
            "LEFT|RIGHT|UP|DOWN|SPACE|ENTER|TAB|BACK_SPACE|"
            + "[A-Z]|DIGIT[0-9]|NUMPAD[0-9]|F(?:[1-9]|1[0-2])"
        );
    }

    // 화면 크기
    public DisplaySize getDisplaySize() {
        return displaySize;
    }

    public void setDisplaySize(DisplaySize displaySize) {
        this.displaySize = displaySize;
    }

    // 난이도
    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    // 왼쪽 이동
    public String getKeyLeft() {
        return keyLeft;
    }

    public void setKeyLeft(String keyLeft) {
        this.keyLeft = keyLeft;
    }

    // 오른쪽 이동
    public String getKeyRight() {
        return keyRight;
    }

    public void setKeyRight(String keyRight) {
        this.keyRight = keyRight;
    }

    // 아래 이동
    public String getKeyDown() {
        return keyDown;
    }

    public void setKeyDown(String keyDown) {
        this.keyDown = keyDown;
    }

    // 회전
    public String getKeyRotate() {
        return keyRotate;
    }

    public void setKeyRotate(String keyRotate) {
        this.keyRotate = keyRotate;
    }

    // 즉시 낙하
    public String getKeyDrop() {
        return keyDrop;
    }

    public void setKeyDrop(String keyDrop) {
        this.keyDrop = keyDrop;
    }

    // 일시정지
    public String getKeyPause() {
        return keyPause;
    }

    public void setKeyPause(String keyPause) {
        this.keyPause = keyPause;
    }

    // 색맹 모드
    public boolean isColorblindMode() {
        return colorblindMode;
    }

    public void setColorblindMode(boolean colorblindMode) {
        this.colorblindMode = colorblindMode;
    }
}