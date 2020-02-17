package com.google.ar.core.examples.java.helloar;

import com.google.ar.core.Camera;
import com.google.ar.core.Pose;

import java.io.IOException;
import java.util.ArrayList;
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

    public void sendData(Camera camera, ArrayList<HelloArActivity.ColoredAnchor> anchors){

        String cameraPose = getCameraPoseString(camera.getDisplayOrientedPose());
        String anchorPosition = getAnchorsPosition(anchors);

        String data = cameraPose + "," + anchorPosition;

        MediaType MEDIA_TYPE_PLAINTEXT = MediaType.parse("text/plain; charset=utf-8");

        Request request = new Request.Builder()
                .url("http://"+IP_ADDRESS+":3000/")
                .post(RequestBody.create(data, MEDIA_TYPE_PLAINTEXT))
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

    private String getAnchorsPosition(ArrayList<HelloArActivity.ColoredAnchor> anchors) {
        String anchorPosition =  Float.toString(anchors.get(0).anchor.getPose().tx()) + ","
                                + Float.toString(anchors.get(0).anchor.getPose().ty()) + ","
                                + Float.toString(anchors.get(0).anchor.getPose().tz());
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
