import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.net.http.HttpClientService;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
public class RobotConnector extends GameApplication {

    public static void takePicture(){
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/plain; charset=UTF-8");
        headers.put("X-Tritium-Auth-Token", "hALeFOM4uzqpK6KWSr6ADY5Ou9MNXd");
        String text = "";

        FXGL.getTaskService().runAsync(FXGL.getService(HttpClientService.class)
                .sendPUTRequestTask("https://rt-0143.robothespian.co.uk/tritium/text_to_speech/say"
                        ,text,
                        headers)
                .onSuccess(resp -> {
                    System.out.println("Success! " + resp);
                })
                .onFailure(e -> {
                    System.out.println("Fail! " + e);
                }));
    }
    public void savePicture(){}
    public void sendImage(){}
    //M:\GameByte Novel Compression Solution\GameByte\src\main\resources\assets\textures\

    public static void main(String[] args) {
        //takePicture();
        launch(args);
    }

    @Override
    protected void initSettings(GameSettings gameSettings) {
        gameSettings.addEngineService(HttpClientService.class);
    }

    @Override
    protected void initGame() {
        //takePicture();
        String imagePath = "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\RobotTest.jpg";
        captureImageToFile(Path.of(imagePath));
    }

    public void captureImageToFile(Path file)
    {
        FXGL.getService(HttpClientService.class).sendGETRequestTask("https://rt-0143.robothespian.co.uk/tritium/video_capture/jpeg", Map.of("X-Tritium-Auth-Token", getKey()),
                HttpResponse.BodyHandlers.ofFile(file))
                .onSuccess(res -> System.out.println(res.statusCode()))
                .onFailure(e->{
                    //
                })
                .run();
    }

    private String getKey(){
        return "hALeFOM4uzqpK6KWSr6ADY5Ou9MNXd";
    }
}
