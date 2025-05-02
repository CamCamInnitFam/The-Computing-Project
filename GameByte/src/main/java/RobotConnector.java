import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.net.http.HttpClientService;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
public class RobotConnector extends GameApplication {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    protected void initSettings(GameSettings gameSettings) {
        gameSettings.addEngineService(HttpClientService.class);
    }

    @Override
    protected void initGame() {
        String imagePath = "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\RobotTest.jpg";
        captureImageToFile(Path.of(imagePath));
    }

    public void captureImageToFile(Path file)
    {
        FXGL.getService(HttpClientService.class)
                .sendGETRequestTask("https://rt-0143.robothespian.co.uk/tritium/video_capture/jpeg",
                        Map.of("X-Tritium-Auth-Token", getKey()),
                HttpResponse.BodyHandlers.ofFile(file))
                .onSuccess(res -> System.out.println(res.statusCode()))
                .onFailure(e->{
                    System.out.println("Error! Get request failure.");
                })
                .run();
    }

    private String getKey(){
        return "";
    }
}
