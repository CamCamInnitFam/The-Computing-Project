import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.ui.FXGLButton;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.media.AudioClip;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.application.Platform;

import java.io.File;
import java.net.URL;
import java.util.function.Consumer;

public class GameByteApp extends GameApplication {
    public static void main(String[] args) {
        launch(args);
    }

    private File compressFile;
    private File decompressFile;

    private ImageView compressPreview;
    private ImageView decompressPreview;
    private ProgressBar compressProgressBar;
    private ProgressBar decompressProgressBar;

    private AudioClip successSound;

    private Label compressDropLabel;
    private Label decompressDropLabel;

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setTitle("GameByte Compressor");
        settings.setWidth(900);
        settings.setHeight(700);
    }

    @Override
    protected void initUI() {
        // Root pane with background color
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f4f0e4;");

        // Logo
        ImageView logo = new ImageView();
        String[] logoPaths = {"/assets/textures/gamebyte_logo.png", "/gamebyte_logo.png"};
        for (String p : logoPaths) {
            URL u = getClass().getResource(p);
            if (u != null) {
                logo.setImage(new Image(u.toExternalForm()));
                logo.setFitWidth(325);
                logo.setPreserveRatio(true);
                break;
            }
        }

        // Reset button (top right)
        FXGLButton resetBtn = (FXGLButton) FXGL.getUIFactoryService().newButton("Reset");
        resetBtn.setStyle("-fx-background-color: #607d8b; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'Roboto', 'Verdana', sans-serif; -fx-font-weight: bold; -fx-border-radius: 5px; -fx-padding: 6 12;");
        resetBtn.setPrefWidth(100);
        resetBtn.setOnAction(e -> clearAll());
        HBox resetBox = new HBox(resetBtn);
        resetBox.setAlignment(Pos.CENTER_RIGHT);
        resetBox.setPadding(new Insets(10)); // Margin from top/right borders
        root.setTop(resetBox);

        // GridPane for compress/decompress sections
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER);
        grid.setPadding(new Insets(5));
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.setPrefWidth(870);

        // Column constraints
        ColumnConstraints col0 = new ColumnConstraints();
        col0.setHgrow(Priority.ALWAYS);
        col0.setPercentWidth(50);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        col1.setPercentWidth(50);
        grid.getColumnConstraints().addAll(col0, col1);

        // Row constraints
        RowConstraints row0 = new RowConstraints();
        row0.setVgrow(Priority.ALWAYS);
        row0.setMaxHeight(260); // Reduced from 300
        RowConstraints row1 = new RowConstraints();
        row1.setVgrow(Priority.NEVER);
        row1.setMinHeight(50);
        grid.getRowConstraints().addAll(row0, row1);

        // Compress section
        Text compressTitle = FXGL.getUIFactoryService().newText("Compressor");
        compressTitle.setFill(Color.DARKBLUE);
        compressTitle.setStyle("-fx-font-size: 18px; -fx-font-family: 'Roboto', 'Verdana', sans-serif; -fx-font-weight: bold;");
        compressDropLabel = createDropLabel("Drag .jpg here", "#ff6f61");
        compressPreview = createPreview();
        compressProgressBar = new ProgressBar(0);
        compressProgressBar.setMaxWidth(Double.MAX_VALUE);
        VBox compressPane = new VBox(3, compressTitle, compressDropLabel, compressPreview, compressProgressBar);
        compressPane.setAlignment(Pos.CENTER);
        compressPane.setStyle("-fx-background-color: #f4f0e4;");
        setupDragHandlers(compressDropLabel, ".jpg", file -> {
            compressFile = file;
            compressDropLabel.setText(file.getName());
            compressPreview.setImage(new Image(file.toURI().toString(), 350, 110, true, true));
        });

        // Decompress section
        Text decompressTitle = FXGL.getUIFactoryService().newText("Decompressor");
        decompressTitle.setFill(Color.DARKBLUE);
        decompressTitle.setStyle("-fx-font-size: 18px; -fx-font-family: 'Roboto', 'Verdana', sans-serif; -fx-font-weight: bold;");
        decompressDropLabel = createDropLabel("Drag .byt here", "#4caf50");
        decompressPreview = createPreview();
        decompressProgressBar = new ProgressBar(0);
        decompressProgressBar.setMaxWidth(Double.MAX_VALUE);
        VBox decompressPane = new VBox(3, decompressTitle, decompressDropLabel, decompressPreview, decompressProgressBar);
        decompressPane.setAlignment(Pos.CENTER);
        decompressPane.setStyle("-fx-background-color: #f4f0e4;");
        setupDragHandlers(decompressDropLabel, ".byt", file -> {
            decompressFile = file;
            decompressDropLabel.setText(file.getName());
            decompressPreview.setImage(null);
        });

        // Buttons
        String compStyle = "-fx-background-color: #ff6f61; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'Roboto', 'Verdana', sans-serif; -fx-font-weight: bold; -fx-border-radius: 5px; -fx-padding: 6 12;";
        String compHover = "-fx-background-color: #e55a50; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'Roboto', 'Verdana', sans-serif; -fx-font-weight: bold; -fx-border-radius: 5px; -fx-padding: 6 12;";
        FXGLButton compressBtn = (FXGLButton) FXGL.getUIFactoryService().newButton("Compress");
        compressBtn.setStyle(compStyle);
        compressBtn.setPrefWidth(180);
        compressBtn.setOnMouseEntered(e -> compressBtn.setStyle(compHover));
        compressBtn.setOnMouseExited(e -> compressBtn.setStyle(compStyle));
        compressBtn.setOnAction(e -> runCompressTask());

        String decompStyle = "-fx-background-color: #4caf50; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'Roboto', 'Verdana', sans-serif; -fx-font-weight: bold; -fx-border-radius: 5px; -fx-padding: 6 12;";
        String decompHover = "-fx-background-color: #3fa045; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'Roboto', 'Verdana', sans-serif; -fx-font-weight: bold; -fx-border-radius: 5px; -fx-padding: 6 12;";
        FXGLButton decompressBtn = (FXGLButton) FXGL.getUIFactoryService().newButton("Decompress");
        decompressBtn.setStyle(decompStyle);
        decompressBtn.setPrefWidth(180);
        decompressBtn.setOnMouseEntered(e -> decompressBtn.setStyle(decompHover));
        decompressBtn.setOnMouseExited(e -> decompressBtn.setStyle(decompStyle));
        decompressBtn.setOnAction(e -> runDecompressTask());

        // Center buttons
        HBox compressBtnBox = new HBox(compressBtn);
        compressBtnBox.setAlignment(Pos.CENTER);
        compressBtnBox.setPadding(new Insets(2));
        HBox decompressBtnBox = new HBox(decompressBtn);
        decompressBtnBox.setAlignment(Pos.CENTER);
        decompressBtnBox.setPadding(new Insets(2));

        // Add to grid
        grid.add(compressPane, 0, 0);
        grid.add(decompressPane, 1, 0);
        grid.add(compressBtnBox, 0, 1);
        grid.add(decompressBtnBox, 1, 1);

        // Main content layout (logo, grid)
        VBox content = new VBox(5, logo, grid);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(5, 15, 15, 15));
        content.setStyle("-fx-background-color: #f4f0e4;");
        content.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(grid, Priority.ALWAYS);

        // Set content in root center
        root.setCenter(content);

        // Add to scene
        FXGL.getGameScene().addUINode(root);
        FXGL.getGameScene().setBackgroundColor(Color.web("#f4f0e4"));

        Platform.runLater(() -> {
            FXGL.getPrimaryStage().setMinWidth(800);
            FXGL.getPrimaryStage().setMinHeight(700);
            FXGL.getPrimaryStage().setResizable(true);
        });

        // Load sound
        URL soundUrl = getClass().getResource("/success.wav");
        if (soundUrl != null) {
            successSound = new AudioClip(soundUrl.toExternalForm());
        }
    }

    private void showPopup(String message, double width, double height) {
        Stage popup = new Stage();
        popup.initOwner(FXGL.getPrimaryStage());
        popup.initStyle(StageStyle.UTILITY);
        popup.setTitle("Operation Complete");
        popup.setResizable(false);

        Text text = new Text(message);
        text.setStyle("-fx-font-size: 12px; -fx-font-family: 'Roboto', 'Verdana', sans-serif;");
        text.setFill(Color.BLACK);
        text.setWrappingWidth(width - 40); // Account for padding

        FXGLButton closeBtn = (FXGLButton) FXGL.getUIFactoryService().newButton("Close");
        closeBtn.setStyle("-fx-background-color: #607d8b; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: 'Roboto', 'Verdana', sans-serif; -fx-font-weight: bold; -fx-border-radius: 5px; -fx-padding: 6 12;");
        closeBtn.setPrefWidth(100);
        closeBtn.setOnAction(e -> popup.close());

        VBox popupContent = new VBox(10, text, closeBtn);
        popupContent.setAlignment(Pos.CENTER);
        popupContent.setPadding(new Insets(15));
        popupContent.setStyle("-fx-background-color: #f4f0e4;");

        popup.setScene(new javafx.scene.Scene(popupContent, width, height));
        popup.setX(FXGL.getPrimaryStage().getX() + (FXGL.getPrimaryStage().getWidth() - width) / 2);
        popup.setY(FXGL.getPrimaryStage().getY() + (FXGL.getPrimaryStage().getHeight() - height) / 2);
        popup.show();
    }

    private Label createDropLabel(String text, String borderColor) {
        Label label = new Label(text);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setPrefWidth(350);
        label.setPrefHeight(50);
        label.setStyle("-fx-border-color: " + borderColor + "; -fx-border-style: dashed; -fx-alignment: center; -fx-font-size: 14px; -fx-font-family: 'Roboto', 'Verdana', sans-serif; -fx-background-color: white; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-padding: 8;");
        return label;
    }

    private ImageView createPreview() {
        ImageView iv = new ImageView();
        iv.setFitWidth(350);
        iv.setFitHeight(110); // Reduced from 130
        iv.setPreserveRatio(true);
        iv.setStyle("-fx-border-color: lightgray; -fx-border-radius: 5px; -fx-background-radius: 5px;");
        return iv;
    }

    private void runCompressTask() {
        if (compressFile == null) {
            showError("Please drag a .jpg file first.");
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                updateProgress(-1, 1);
                String outputPath = compressFile.getParent() + File.separator + "compressed_" + compressFile.getName().replace(".jpg", ".byt");
                try {
                    GameByteCompressor.compress(compressFile.getAbsolutePath(), outputPath);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            }
        };

        bindProgress(task, compressProgressBar);
        task.setOnSucceeded(e -> {
            completeProgress(compressProgressBar);
            playSuccessSound();
            showCompressionResult(compressFile, new File(compressFile.getParent() + File.separator + "compressed_" + compressFile.getName().replace(".jpg", ".byt")));
        });
        task.setOnFailed(e -> handleError(task.getException()));

        new Thread(task).start();
    }

    private void runDecompressTask() {
        if (decompressFile == null) {
            showError("Please drag a .byt file first.");
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                updateProgress(-1, 1);
                String outputPath = decompressFile.getParent() + File.separator + "decompressed_" + decompressFile.getName().replace(".byt", ".jpg");
                try {
                    GameByteDecompressor.decompress(decompressFile.getAbsolutePath(), outputPath);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            }
        };

        bindProgress(task, decompressProgressBar);
        task.setOnSucceeded(e -> {
            completeProgress(decompressProgressBar);
            playSuccessSound();
            File outFile = new File(decompressFile.getParent() + File.separator + "decompressed_" + decompressFile.getName().replace(".byt", ".jpg"));
            showDecompressionResult(decompressFile, outFile);
            if (outFile.exists()) {
                decompressPreview.setImage(new Image(outFile.toURI().toString(), 350, 110, true, true));
            }
        });
        task.setOnFailed(e -> handleError(task.getException()));

        new Thread(task).start();
    }

    private void bindProgress(Task<?> task, ProgressBar progressBar) {
        task.setOnRunning(e -> progressBar.progressProperty().bind(task.progressProperty()));
    }

    private void completeProgress(ProgressBar progressBar) {
        progressBar.progressProperty().unbind();
        progressBar.setProgress(0);
    }

    private void showCompressionResult(File original, File compressed) {
        long orig = original.length();
        long comp = compressed.length();
        double reduction = ((double) (orig - comp) / orig) * 100;
        String message = String.format("Compression Complete!\nSaved to: %s\nOriginal Size: %d bytes\nCompressed Size: %d bytes\n%% Reduction: %.2f%%",
                compressed.getAbsolutePath(), orig, comp, reduction);
        showPopup(message, 400, 200);
    }

    private void showDecompressionResult(File original, File decompressed) {
        String message = String.format("Decompression Complete!\nSaved to: %s", decompressed.getAbsolutePath());
        showPopup(message, 400, 150);
    }

    private void showError(String message) {
        showPopup("Error: " + message, 400, 100);
    }

    private void handleError(Throwable throwable) {
        completeProgress(compressProgressBar);
        completeProgress(decompressProgressBar);
        showError("An error occurred: " + throwable.getMessage());
        throwable.printStackTrace();
    }

    private void clearAll() {
        compressFile = null;
        decompressFile = null;
        compressPreview.setImage(null);
        decompressPreview.setImage(null);
        compressProgressBar.setProgress(0);
        decompressProgressBar.setProgress(0);
        compressDropLabel.setText("Drag .jpg here");
        decompressDropLabel.setText("Drag .byt here");
    }

    private void playSuccessSound() {
        if (successSound != null) {
            successSound.play();
        }
    }

    private void setupDragHandlers(Label label, String extension, Consumer<File> onDrop) {
        label.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles() && db.getFiles().get(0).getName().toLowerCase().endsWith(extension)) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        label.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                File file = db.getFiles().get(0);
                if (file.getName().toLowerCase().endsWith(extension)) {
                    onDrop.accept(file);
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }
}