package com.google.ar.core.examples.java.helloar;

import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ClientWrapper {

    private OkHttpClient client;
    private static final String IP_ADDRESS = "localhost";

    public ClientWrapper() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(50, TimeUnit.SECONDS)
                .writeTimeout(50, TimeUnit.SECONDS)
                .readTimeout(70, TimeUnit.SECONDS)
                .build();
    }

    public void sendData(Camera camera, String frameBase64, Anchor anchor, FloatBuffer pointCloudServer) throws JSONException {

        String cameraPose = getCameraPoseString(camera.getPose());
        String cameraDisplayOrientedPose = getCameraPoseString(camera.getDisplayOrientedPose());
        String anchorPosition = getAnchorsPosition(anchor);
        String pointCloud = getPointCloudAsString(pointCloudServer);

        JSONObject postData = new JSONObject();
        postData.put("cameraPose", cameraPose);
        postData.put("cameraDisplayOrientedPose", cameraDisplayOrientedPose);
        postData.put("anchorPosition", anchorPosition);
        postData.put("frameString", frameBase64);
        postData.put("pointCloud", pointCloud);

        MediaType JSON = MediaType.get("application/json; charset=utf-8");

        Request request = new Request.Builder()
                .url("http://"+IP_ADDRESS+":3000/")
                .post(RequestBody.create(postData.toString(), JSON))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {

                    if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
                    System.out.println("HTTP Request Done");

                }
            }
        });
    }

    private String getPointCloudAsString(FloatBuffer pointCloudServer) {

        String points3DText = "";

        if(pointCloudServer == null){
            return "";
        }

        while (pointCloudServer.hasRemaining()){

            //these two need to be declared here!
            double[] screenPoint = new double[]{0,0};
            double[] correspondence2D3D = new double[]{0,0,0,0,0};

            float x = pointCloudServer.get();
            float y = pointCloudServer.get();
            float z = pointCloudServer.get();
            float c = pointCloudServer.get();

            points3DText = points3DText.concat(x + " " + y + " " + z + " " + c + "\n");

        }

        return points3DText;
    }

    private String getAnchorsPosition(Anchor anchor) {
        String anchorPosition =  Float.toString(anchor.getPose().tx()) + ","
                                + Float.toString(anchor.getPose().ty()) + ","
                                + Float.toString(anchor.getPose().tz());
        return anchorPosition;
    }

    private String getCameraPoseString(Pose pose) {
        String poseString = Float.toString(pose.tx()) + ","
                            + Float.toString(pose.ty()) + ","
                            + Float.toString(pose.tz()) + ","
                            + Float.toString(pose.qx()) + ","
                            + Float.toString(pose.qy()) + ","
                            + Float.toString(pose.qz()) + ","
                            + Float.toString(pose.qw());

        return poseString;
    }
}
