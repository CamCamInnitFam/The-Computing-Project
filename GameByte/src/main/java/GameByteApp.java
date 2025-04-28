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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.AudioClip;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import java.io.File;
import java.net.URL;
import java.util.function.Consumer;

public class GameByteApp extends GameApplication {
    public static void main(String[] args) {
        launch(args);
    }

    private File compressFile;
    private File decompressFile;

    private Text resultText;
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
        settings.setWidth(1000);
        settings.setHeight(700);
    }

    @Override
    protected void initUI() {
        // Root pane
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f9f6ef;");

        // Title
        Text title = FXGL.getUIFactoryService().newText("GameByte");
        title.setFill(Color.DARKBLUE);
        title.setStyle("-fx-font-size: 36px;");

        // Logo
        ImageView logo = new ImageView();
        String[] logoPaths = {"/assets/textures/gamebyte_logo.png", "/gamebyte_logo.png"};
        for (String p : logoPaths) {
            URL u = getClass().getResource(p);
            if (u != null) {
                logo.setImage(new Image(u.toExternalForm()));
                logo.setFitWidth(200);
                logo.setPreserveRatio(true);
                break;
            }
        }

        // --- Sections in GridPane ---
        GridPane grid = new GridPane();
        grid.setHgap(100);
        grid.setVgap(20);
        grid.setAlignment(Pos.CENTER);

        // Compress column
        compressDropLabel = createDropLabel();
        compressPreview = createPreview();
        compressProgressBar = new ProgressBar(0);
        compressProgressBar.setPrefWidth(300);
        VBox compressPane = new VBox(10, compressDropLabel, compressPreview, compressProgressBar);
        compressPane.setAlignment(Pos.CENTER);
        setupDragHandlers(compressDropLabel, ".jpg", file -> {
            compressFile = file;
            compressDropLabel.setText(file.getName());
            compressPreview.setImage(new Image(file.toURI().toString(), 300, 0, true, true));
        });

        // Decompress column
        decompressDropLabel = createDropLabel();
        decompressPreview = createPreview();
        decompressProgressBar = new ProgressBar(0);
        decompressProgressBar.setPrefWidth(300);
        VBox decompressPane = new VBox(10, decompressDropLabel, decompressPreview, decompressProgressBar);
        decompressPane.setAlignment(Pos.CENTER);
        setupDragHandlers(decompressDropLabel, ".byt", file -> {
            decompressFile = file;
            decompressDropLabel.setText(file.getName());
            decompressPreview.setImage(null);
        });

        // Buttons
        String compStyle = "-fx-background-color: #ff6f61; -fx-text-fill: white; -fx-font-size: 18px; -fx-pref-width: 160px;";
        String compHover = "-fx-background-color: #e55a50; -fx-text-fill: white; -fx-font-size: 18px; -fx-pref-width: 160px;";
        FXGLButton compressBtn = (FXGLButton) FXGL.getUIFactoryService().newButton("Compress");
        compressBtn.setStyle(compStyle);
        compressBtn.setOnMouseEntered(e -> compressBtn.setStyle(compHover));
        compressBtn.setOnMouseExited(e -> compressBtn.setStyle(compStyle));
        compressBtn.setOnAction(e -> runCompressTask());

        String decompStyle = "-fx-background-color: #4caf50; -fx-text-fill: white; -fx-font-size: 18px; -fx-pref-width: 160px;";
        String decompHover = "-fx-background-color: #3fa045; -fx-text-fill: white; -fx-font-size: 18px; -fx-pref-width: 160px;";
        FXGLButton decompressBtn = (FXGLButton) FXGL.getUIFactoryService().newButton("Decompress");
        decompressBtn.setStyle(decompStyle);
        decompressBtn.setOnMouseEntered(e -> decompressBtn.setStyle(decompHover));
        decompressBtn.setOnMouseExited(e -> decompressBtn.setStyle(decompStyle));
        decompressBtn.setOnAction(e -> runDecompressTask());

        // Place panes and buttons in grid
        grid.add(compressPane, 0, 0);
        grid.add(compressBtn, 0, 1);
        grid.add(decompressPane, 1, 0);
        grid.add(decompressBtn, 1, 1);

        // Result and reset
        resultText = FXGL.getUIFactoryService().newText("");
        resultText.setFill(Color.BLACK);
        FXGLButton resetBtn = (FXGLButton) FXGL.getUIFactoryService().newButton("Reset");
        resetBtn.setStyle("-fx-background-color: #607d8b; -fx-text-fill: white; -fx-font-size: 14px;");
        resetBtn.setOnAction(e -> clearAll());
        HBox bottom = new HBox(20, resultText, resetBtn);
        bottom.setAlignment(Pos.CENTER);

        // Assemble content
        VBox content = new VBox(20, title, logo, grid, bottom);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(20));
        root.setCenter(content);

        FXGL.getGameScene().addUINode(root);

        // Load success sound
        URL soundUrl = getClass().getResource("/success.wav");
        if (soundUrl != null) {
            successSound = new AudioClip(soundUrl.toExternalForm());
        }
    }

    private Label createDropLabel() {
        Label label = new Label("Drag here");
        label.setPrefSize(300, 50);
        label.setStyle("-fx-border-color: gray; -fx-border-style: dashed; -fx-alignment: center; -fx-font-size: 14px; -fx-background-color: white; -fx-border-radius: 5px; -fx-background-radius: 5px;");
        return label;
    }

    private ImageView createPreview() {
        ImageView iv = new ImageView();
        iv.setFitWidth(300);
        iv.setFitHeight(200);
        iv.setPreserveRatio(true);
        iv.setStyle("-fx-border-color: lightgray; -fx-background-radius: 5px;");
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
                decompressPreview.setImage(new Image(outFile.toURI().toString(), 200, 0, true, true));
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
        double rate = ((double) (orig - comp) / orig) * 100;
        resultText.setFill(Color.BLACK);
        resultText.setText(String.format("Compressed: %s\nOriginal: %d bytes\nCompressed: %d bytes\nRate: %.2f%%",
                compressed.getName(), orig, comp, rate));
    }

    private void showDecompressionResult(File original, File decompressed) {
        long orig = original.length();
        long decomp = decompressed.length();
        double rate = ((double) (decomp - orig) / orig) * 100;
        resultText.setFill(Color.BLACK);
        resultText.setText(String.format("Decompressed: %s\nOriginal: %d bytes\nDecompressed: %d bytes\nExpansion: %.2f%%",
                decompressed.getName(), orig, decomp, rate));
    }

    private void showError(String message) {
        resultText.setFill(Color.RED);
        resultText.setText(message);
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
        resultText.setText("");
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
