// File: GameByteApp.java

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.ui.FXGLButton;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
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

    private ImageView logo;
    private int logoIndex = 0;

    long startTime;
    long endTime;

    private final String[] slideshowImages = {
            "/assets/textures/Slideshow/gamebyte_logo.png",
            "/assets/textures/Slideshow/Slide2.PNG",
            "/assets/textures/Slideshow/Slide3.PNG",
            "/assets/textures/Slideshow/Slide4.PNG",
            "/assets/textures/Slideshow/Slide5.PNG",
            "/assets/textures/Slideshow/Slide6.PNG"
    };

    private CheckBox pngCheckBox;

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setTitle("GameByte");
        settings.setWidth(1920);
        settings.setHeight(1080);
    }

    @Override
    protected void initUI() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f4f0e4;");

        logo = new ImageView();
        logo.setFitWidth(400);
        logo.setPreserveRatio(true);
        updateLogoImage();

        FXGLButton prevBtn = createStyledButton("Previous", "#4caf50", "#3e8e41");
        FXGLButton nextBtn = createStyledButton("Next", "#ff6f61", "#e65b50");

        prevBtn.setOnAction(e -> {
            logoIndex = (logoIndex - 1 + slideshowImages.length) % slideshowImages.length;
            updateLogoImage();
        });
        nextBtn.setOnAction(e -> {
            logoIndex = (logoIndex + 1) % slideshowImages.length;
            updateLogoImage();
        });

        HBox logoBox = new HBox(30, prevBtn, logo, nextBtn);
        logoBox.setAlignment(Pos.CENTER);
        logoBox.setPadding(new Insets(20));
        logoBox.setPrefWidth(1920);

        compressDropLabel = createDropLabel("Drag .jpg/.png here", "#ff6f61");
        compressPreview = createPreview();
        compressProgressBar = new ProgressBar(0);
        compressProgressBar.setMaxWidth(Double.MAX_VALUE);
        setupDragHandlers(compressDropLabel, new String[]{".jpg", ".png"}, file -> {
            compressFile = file;
            compressDropLabel.setText(file.getName());
            compressPreview.setImage(new Image(file.toURI().toString(), 350, 150, true, true));
        });

        FXGLButton compressBtn = createStyledButton("Compress", "#ff6f61", "#e65b50");
        compressBtn.setPrefWidth(250);
        compressBtn.setOnAction(e -> runCompressTask());
        Text compressorText = FXGL.getUIFactoryService().newText("Compressor");
        compressorText.setFill(Color.RED);

        VBox compressPane = new VBox(15,
                compressorText,
                compressDropLabel,
                compressPreview,
                compressProgressBar,
                compressBtn);
        compressPane.setAlignment(Pos.TOP_CENTER);
        compressPane.setPrefWidth(900);

        decompressDropLabel = createDropLabel("Drag .byt here", "#4caf50");
        decompressPreview = createPreview();
        decompressProgressBar = new ProgressBar(0);
        decompressProgressBar.setMaxWidth(Double.MAX_VALUE);
        setupDragHandlers(decompressDropLabel, new String[]{".byt"}, file -> {
            decompressFile = file;
            decompressDropLabel.setText(file.getName());
            decompressPreview.setImage(null);
        });

        FXGLButton decompressBtn = createStyledButton("Decompress", "#4caf50", "#3e8e41");
        decompressBtn.setPrefWidth(250);
        decompressBtn.setOnAction(e -> runDecompressTask());

        pngCheckBox = new CheckBox("Save as PNG");
        pngCheckBox.setSelected(false);
        Text decompressText = FXGL.getUIFactoryService().newText("Decompressor");
        decompressText.setFill(Color.GREEN);

        VBox decompressPane = new VBox(15,
                decompressText,
                decompressDropLabel,
                decompressPreview,
                decompressProgressBar,
                decompressBtn,
                pngCheckBox);
        decompressPane.setAlignment(Pos.TOP_CENTER);
        decompressPane.setPrefWidth(900);


        HBox ioBox = new HBox(60, compressPane, decompressPane);
        ioBox.setAlignment(Pos.TOP_CENTER);
        ioBox.setPadding(new Insets(30));
        ioBox.setPrefWidth(1920);

        FXGLButton resetBtn = createStyledButton("Reset", "#607d8b", "#4e6b7a");
        resetBtn.setPrefWidth(250);
        resetBtn.setOnAction(e -> clearAll());

        HBox resetBox = new HBox(resetBtn);
        resetBox.setAlignment(Pos.CENTER);
        resetBox.setPadding(new Insets(20));

        VBox layout = new VBox(logoBox, ioBox, resetBox);
        layout.setAlignment(Pos.TOP_CENTER);
        layout.setPadding(new Insets(10));
        layout.setPrefWidth(1920);
        layout.setPrefHeight(1080);

        root.setCenter(layout);
        FXGL.getGameScene().addUINode(root);

        URL soundUrl = getClass().getResource("/assets/textures/Audio/done.wav");
        if (soundUrl != null) {
            successSound = new AudioClip(soundUrl.toExternalForm());
        }

        Platform.runLater(() -> {
            FXGL.getPrimaryStage().setMinWidth(1920);
            FXGL.getPrimaryStage().setMinHeight(1080);
            FXGL.getPrimaryStage().setResizable(false);
        });
    }

    private FXGLButton createStyledButton(String text, String baseColor, String hoverColor) {
        FXGLButton btn = (FXGLButton) FXGL.getUIFactoryService().newButton(text);
        btn.setStyle("-fx-background-color: " + baseColor + "; -fx-text-fill: black;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: " + hoverColor + "; -fx-text-fill: black;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: " + baseColor + "; -fx-text-fill: black;"));
        return btn;
    }

    private void updateLogoImage() {
        URL u = getClass().getResource(slideshowImages[logoIndex]);
        if (u != null) {
            logo.setImage(new Image(u.toExternalForm()));
        }
    }

    private void runCompressTask() {
        if (compressFile == null) {
            showError("Please drag a file first.");
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateProgress(-1, 1);
                String ext = compressFile.getName().endsWith(".png") ? ".png" : ".jpg";
                String outputPath = compressFile.getParent() + "/compressed_" + compressFile.getName().replace(ext, ".byt");
                startTime = System.nanoTime();
                GameByteCompressor.compress(compressFile.getAbsolutePath(), outputPath);
                return null;
            }
        };

        bindProgress(task, compressProgressBar);
        task.setOnSucceeded(e -> {
            endTime = System.nanoTime();
            completeProgress(compressProgressBar);
            playSuccessSound();
            File outFile = new File(compressFile.getParent(), "compressed_" + compressFile.getName().replaceAll("\\.(png|jpg)", ".byt"));
            long size2 = outFile.length();
            long size1 = compressFile.length();
            long differenceBytes = size1 - size2;
            double differenceKB = (double) differenceBytes / 1024;
            double compressionPercent = ((double) differenceBytes / size1) * 100;

            double timeTakenS = (endTime - startTime) / 1_000_000_000.0;
            String timeTaken = String.format("%.2f", timeTakenS) + " seconds";
            String valueResult = String.format("Compression reduction: %.2f%%\nDifference: %d bytes (%.2f KB)", compressionPercent, differenceBytes, differenceKB);
            showPopup("Compression complete!\nSaved as: " + outFile.getAbsolutePath() + "\nOriginal Size: " + size1 + " bytes" + "\nCompressed Size: " + size2 + " bytes" + "\n" + valueResult + "\nTime taken: " + timeTaken, 800, 250);
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
            protected Void call() throws Exception {
                updateProgress(-1, 1);
                String format = pngCheckBox.isSelected() ? ".png" : ".jpg";
                String outputPath = decompressFile.getParent() + "/decompressed_" + decompressFile.getName().replace(".byt", format);
                GameByteDecompressor.decompress(decompressFile.getAbsolutePath(), outputPath);
                return null;
            }
        };

        bindProgress(task, decompressProgressBar);
        task.setOnSucceeded(e -> {
            completeProgress(decompressProgressBar);
            playSuccessSound();
            String format = pngCheckBox.isSelected() ? ".png" : ".jpg";
            File outFile = new File(decompressFile.getParent(), "decompressed_" + decompressFile.getName().replace(".byt", format));
            decompressPreview.setImage(new Image(outFile.toURI().toString(), 350, 150, true, true));
            showPopup("Decompression complete: " + outFile.getName(), 400, 200);
        });
        task.setOnFailed(e -> handleError(task.getException()));
        new Thread(task).start();
    }

    private void bindProgress(Task<?> task, ProgressBar progressBar) {
        progressBar.progressProperty().bind(task.progressProperty());
    }

    private void completeProgress(ProgressBar bar) {
        bar.progressProperty().unbind();
        bar.setProgress(0);
    }

    private Label createDropLabel(String text, String borderColor) {
        Label label = new Label(text);
        label.setPrefSize(350, 50);
        label.setStyle("-fx-border-color: " + borderColor + "; -fx-border-style: dashed; -fx-background-color: white; -fx-alignment: center;");
        return label;
    }

    private ImageView createPreview() {
        ImageView iv = new ImageView();
        iv.setFitWidth(350);
        iv.setFitHeight(150);
        iv.setPreserveRatio(true);
        iv.setStyle("-fx-border-color: lightgray;");
        return iv;
    }

    private void clearAll() {
        compressFile = null;
        decompressFile = null;
        compressDropLabel.setText("Drag .jpg/.png here");
        decompressDropLabel.setText("Drag .byt here");
        compressPreview.setImage(null);
        decompressPreview.setImage(null);
        compressProgressBar.setProgress(0);
        decompressProgressBar.setProgress(0);
    }

    private void showPopup(String message, double width, double height) {
        Stage popup = new Stage();
        popup.initOwner(FXGL.getPrimaryStage());
        popup.initStyle(StageStyle.UTILITY);
        popup.setTitle("Info");

        Text text = new Text(message);
        FXGLButton closeBtn = (FXGLButton) FXGL.getUIFactoryService().newButton("Close");
        closeBtn.setOnAction(e -> popup.close());

        VBox content = new VBox(10, text, closeBtn);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(15));
        popup.setScene(new Scene(content, width, height));
        popup.show();
    }

    private void handleError(Throwable throwable) {
        completeProgress(compressProgressBar);
        completeProgress(decompressProgressBar);
        showError("An error occurred: " + throwable.getMessage());
    }

    private void showError(String message) {
        showPopup("Error: " + message, 400, 150);
    }

    private void setupDragHandlers(Label label, String[] extensions, Consumer<File> onDrop) {
        label.setOnDragOver(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasFiles()) {
                for (String ext : extensions) {
                    if (db.getFiles().get(0).getName().toLowerCase().endsWith(ext)) {
                        e.acceptTransferModes(TransferMode.COPY);
                        break;
                    }
                }
            }
            e.consume();
        });

        label.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                File file = db.getFiles().get(0);
                for (String ext : extensions) {
                    if (file.getName().toLowerCase().endsWith(ext)) {
                        onDrop.accept(file);
                        success = true;
                        break;
                    }
                }
            }
            e.setDropCompleted(success);
            e.consume();
        });
    }

    private void playSuccessSound() {
        if (successSound != null) {
            successSound.play();
        }
    }
}
