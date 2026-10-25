package seoultech.se.tetris.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;

// 마우스/키보드 입력 강조와 팝업 안의 키보드 이동을 공통 처리
final class UIInteraction {
    private static final Object SCENE_STATE = new Object();
    private static final Object BUTTON_STATE = new Object();
    private static final PseudoClass INPUT_HIGHLIGHT = PseudoClass.getPseudoClass("input-highlight");
    private static final PseudoClass NEW_RECORD = PseudoClass.getPseudoClass("new-record");
    private static final String STYLESHEET = UIInteraction.class.getResource("css/Interaction.css").toExternalForm();
    private static boolean keyboardInput;

    private UIInteraction() {}

    static void trackInput(Scene scene) {
        state(scene);
    }

    static void installStyles(Parent root) {
        if (!root.getStylesheets().contains(STYLESHEET)) {
            root.getStylesheets().add(STYLESHEET);
        }
    }

    static void installNavigation(Parent root, Node first) {
        installNavigation(root, first, () -> false);
    }

    static void installNavigation(Parent root, Node first, BooleanSupplier capturingKey) {
        installStyles(root);
        Scope scope = new Scope(root, first, capturingKey);
        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null) {
                state(oldScene).remove(scope);
            }
            if (newScene != null) {
                state(newScene).add(scope);
            }
        });
        if (root.getScene() != null) {
            state(root.getScene()).add(scope);
        }
    }

    static void installButton(ButtonBase button) {
        if (button.getProperties().containsKey(BUTTON_STATE)) {
            return;
        }
        ButtonState interaction = new ButtonState(button);
        button.getProperties().put(BUTTON_STATE, interaction);
        button.getStyleClass().add("interactive-button");
        button.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null) {
                state(oldScene).buttons.remove(interaction);
                button.pseudoClassStateChanged(INPUT_HIGHLIGHT, false);
            }
            if (newScene != null) {
                state(newScene).buttons.add(interaction);
                state(newScene).refresh();
            }
        });
        button.focusedProperty().addListener((observable, oldValue, focused) -> interaction.refresh());
        button.disabledProperty().addListener((observable, oldValue, disabled) -> interaction.refresh());
        button.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> interaction.hover(true));
        button.addEventHandler(MouseEvent.MOUSE_EXITED, e -> interaction.hover(false));
        if (button.getScene() != null) {
            state(button.getScene()).buttons.add(interaction);
        }
    }

    // 세 칸 사이에는 세로선을 넣지 않고 새 기록 한 줄의 바깥만 강조
    static void markRecord(Region cell, boolean highlighted, int column) {
        if (!cell.getStyleClass().contains("record-cell")) {
            cell.getStyleClass().add("record-cell");
            if (column == 0) cell.getStyleClass().add("record-start");
            if (column == 2) cell.getStyleClass().add("record-end");
        }
        cell.pseudoClassStateChanged(NEW_RECORD, highlighted);
    }

    private static SceneState state(Scene scene) {
        return (SceneState) scene.getProperties().computeIfAbsent(SCENE_STATE, key -> new SceneState(scene));
    }

    private static boolean contains(Parent root, Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == root) return true;
        }
        return false;
    }

    private static boolean available(Node node) {
        if (node == null || node.isDisabled() || !node.isFocusTraversable()) return false;
        for (Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible()) return false;
        }
        return true;
    }

    private static void collect(Parent root, List<Node> controls) {
        for (Node child : root.getChildrenUnmodifiable()) {
            if (!child.isVisible() || child.isDisabled()) continue;
            if ((child instanceof ButtonBase || child instanceof TextInputControl) && available(child)) {
                controls.add(child);
            } else if (child instanceof Parent parent) {
                collect(parent, controls);
            }
        }
    }

    private static final class Scope {
        final Parent root;
        final Node first;
        final BooleanSupplier capturingKey;
        Node previousFocus;

        Scope(Parent root, Node first, BooleanSupplier capturingKey) {
            this.root = root;
            this.first = first;
            this.capturingKey = capturingKey;
        }

        List<Node> controls() {
            List<Node> controls = new ArrayList<>();
            collect(root, controls);
            return controls;
        }

        void focusFirst() {
            if (available(first) && first.getScene() == root.getScene()) {
                first.requestFocus();
            } else {
                List<Node> controls = controls();
                if (!controls.isEmpty()) controls.getFirst().requestFocus();
            }
        }
    }

    private static final class ButtonState {
        final ButtonBase button;
        boolean hovered;

        ButtonState(ButtonBase button) { this.button = button; }

        void hover(boolean value) {
            hovered = value;
            Scene scene = button.getScene();
            if (scene != null) state(scene).useMouse();
        }

        void refresh() {
            if (button.getScene() != null) state(button.getScene()).refresh();
        }
    }

    private static final class SceneState {
        final Scene scene;
        final Set<ButtonState> buttons = new LinkedHashSet<>();
        final List<Scope> scopes = new ArrayList<>();
        boolean keyboard = keyboardInput;

        SceneState(Scene scene) {
            this.scene = scene;
            scene.focusOwnerProperty().addListener((observable, oldFocus, newFocus) -> refresh());
            scene.addEventFilter(KeyEvent.KEY_PRESSED, this::keyPressed);
            scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> useMouse());
            scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> useMouse());
        }

        Scope active() { return scopes.isEmpty() ? null : scopes.getLast(); }

        void add(Scope scope) {
            scope.previousFocus = scene.getFocusOwner();
            scopes.add(scope);
            scope.focusFirst();
            Platform.runLater(() -> {
                if (active() == scope && scope.root.getScene() == scene
                        && (!contains(scope.root, scene.getFocusOwner())
                            || !(scene.getFocusOwner() instanceof ButtonBase || scene.getFocusOwner() instanceof TextInputControl))) {
                    scope.focusFirst();
                }
            });
            refresh();
        }

        void remove(Scope scope) {
            boolean wasActive = active() == scope;
            scopes.remove(scope);
            if (wasActive && active() != null) {
                Node previous = scope.previousFocus;
                if (available(previous) && previous.getScene() == scene && contains(active().root, previous)) {
                    previous.requestFocus();
                } else {
                    active().focusFirst();
                }
            }
            refresh();
        }

        void useMouse() {
            keyboard = false;
            keyboardInput = false;
            refresh();
        }

        void refresh() {
            Scope active = active();
            for (ButtonState interaction : buttons) {
                ButtonBase button = interaction.button;
                boolean inScope = active == null || contains(active.root, button);
                boolean highlighted = !button.isDisabled() && inScope
                    && (keyboard ? scene.getFocusOwner() == button : interaction.hovered);
                button.pseudoClassStateChanged(INPUT_HIGHLIGHT, highlighted);
            }
        }

        void keyPressed(KeyEvent event) {
            keyboard = true;
            keyboardInput = true;
            refresh();
            Scope scope = active();
            if (scope == null) return;
            // 키 설정 대기 중에는 화살표/Tab/Enter도 새 키로 전달
            if (scope.capturingKey.getAsBoolean()) return;
            KeyCode code = event.getCode();
            if (code != KeyCode.TAB && !code.isArrowKey() && code != KeyCode.ENTER && code != KeyCode.SPACE) return;
            Node focused = scene.getFocusOwner();
            if (!contains(scope.root, focused)) {
                scope.focusFirst();
                focused = scene.getFocusOwner();
            }
            List<Node> controls = scope.controls();
            if (controls.isEmpty()) return;
            if (code == KeyCode.TAB) {
                int index = controls.indexOf(focused);
                int next = Math.floorMod(index + (event.isShiftDown() ? -1 : 1), controls.size());
                controls.get(next).requestFocus();
            } else if (focused instanceof TextInputControl) {
                return;
            } else if (code.isArrowKey()) {
                move(scope, focused, code, controls);
            } else if (focused instanceof ButtonBase button) {
                button.fire();
            } else {
                return;
            }
            event.consume();
            refresh();
        }

        void move(Scope scope, Node focused, KeyCode code, List<Node> controls) {
            if (focused == null) { scope.focusFirst(); return; }
            scope.root.applyCss();
            scope.root.layout();
            Bounds origin = focused.localToScene(focused.getLayoutBounds());
            boolean vertical = code == KeyCode.UP || code == KeyCode.DOWN;
            boolean forward = code == KeyCode.DOWN || code == KeyCode.RIGHT;
            List<Node> candidates = controls.stream().filter(node -> node != focused).filter(node -> {
                Bounds target = node.localToScene(node.getLayoutBounds());
                double delta = vertical ? target.getCenterY() - origin.getCenterY() : target.getCenterX() - origin.getCenterX();
                return forward ? delta > 1 : delta < -1;
            }).toList();
            candidates.stream().min(Comparator
                .comparingInt((Node node) -> {
                    Bounds target = node.localToScene(node.getLayoutBounds());
                    boolean overlaps = vertical
                        ? target.getMaxX() > origin.getMinX() && target.getMinX() < origin.getMaxX()
                        : target.getMaxY() > origin.getMinY() && target.getMinY() < origin.getMaxY();
                    return overlaps ? 0 : 1;
                })
                .thenComparingDouble(node -> {
                    Bounds target = node.localToScene(node.getLayoutBounds());
                    double dx = Math.abs(target.getCenterX() - origin.getCenterX());
                    double dy = Math.abs(target.getCenterY() - origin.getCenterY());
                    return vertical ? dy + dx * 0.25 : dx + dy * 0.25;
                })).ifPresent(Node::requestFocus);
        }
    }
}
