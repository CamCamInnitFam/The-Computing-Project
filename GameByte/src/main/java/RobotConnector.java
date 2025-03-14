import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.net.http.HttpClientService;

import java.util.HashMap;
import java.util.Map;
public class RobotConnector {

    public void takePicture(){
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/plain; charset=UTF-8");
        headers.put("X-Tritium-Auth-Token", "");
        String text = "";
        FXGL.getTaskService().runAsync(FXGL.getService(HttpClientService.class)
                .sendPUTRequestTask("https://rt-0143.robothespian.co.uk/"
                        ,text,
                        headers)
                .onSuccess(resp -> {

                })
                .onFailure(e -> {

                }));
    }
    public void savePicture(){}
    public void sendImage(){}
}
