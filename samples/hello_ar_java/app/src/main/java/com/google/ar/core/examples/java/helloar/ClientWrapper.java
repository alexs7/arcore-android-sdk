package com.google.ar.core.examples.java.helloar;

import android.opengl.Matrix;

import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Pose;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.FloatBuffer;
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
    private CallBackAction callBackAction;
    private static final String IP_ADDRESS = "localhost";
    private Gson gson;

    public ClientWrapper() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(50, TimeUnit.SECONDS)
                .writeTimeout(50, TimeUnit.SECONDS)
                .readTimeout(70, TimeUnit.SECONDS)
                .build();
        gson = new Gson();
    }

    public void setCallBackActionListener(CallBackAction callBackAction) {
        this.callBackAction = callBackAction;
    }

    public void sendData(Camera camera, String frameBase64, Anchor anchor, float[] projmtx, float[] viewmtx, FloatBuffer pointCloudServer) throws JSONException {

        String cameraPose = getCameraPoseString(camera.getPose());
        String cameraPoseCamCenter = getCameraCenter(camera.getPose());
        String cameraPoseLocalAxes = getLocalAxes(camera.getPose());

        String cameraDisplayOrientedPose = getCameraPoseString(camera.getDisplayOrientedPose());
        String cameraDisplayOrientedPoseLocalAxes = getLocalAxes(camera.getDisplayOrientedPose());
        String cameraDisplayOrientedPoseCamCenter = getCameraCenter(camera.getDisplayOrientedPose());

        String viewMatrix = getMatrixString(viewmtx);
        String projMatrix = getMatrixString(projmtx);

        float[] matrix = new float[16];
        camera.getPose().toMatrix(matrix,0);
        String cameraPoseMatrix = getMatrixString(matrix);

        matrix = new float[16];
        camera.getDisplayOrientedPose().toMatrix(matrix,0);
        String cameraDisplayOrientedPoseMatrix = getMatrixString(matrix);

        String debugAnchorPositionForCameraPose =  getDebugAnchorPosition(new float[]{1.f,0.f,0.f}, camera.getPose());
        String debugAnchorPositionForDisplayOrientedPose =  getDebugAnchorPosition(new float[]{1.f,0.f,0.f}, camera.getDisplayOrientedPose());

        String anchorPosition = getAnchorsPosition(anchor);
        String pointCloud = getPointCloudAsString(pointCloudServer);

        JSONObject postData = new JSONObject();
        postData.put("cameraPose", cameraPose);
        postData.put("cameraDisplayOrientedPose", cameraDisplayOrientedPose);
        postData.put("anchorPosition", anchorPosition);
        postData.put("frameString", frameBase64);
        postData.put("pointCloud", pointCloud);
        postData.put("cameraPoseLocalAxes", cameraPoseLocalAxes);
        postData.put("cameraDisplayOrientedPoseLocalAxes", cameraDisplayOrientedPoseLocalAxes);
        postData.put("cameraPoseCamCenter", cameraPoseCamCenter);
        postData.put("cameraDisplayOrientedPoseCamCenter", cameraDisplayOrientedPoseCamCenter);
        postData.put("debugAnchorPositionForCameraPose", debugAnchorPositionForCameraPose);
        postData.put("debugAnchorPositionForDisplayOrientedPose", debugAnchorPositionForDisplayOrientedPose);
        postData.put("viewmtx", viewMatrix);
        postData.put("projMatrix", projMatrix);
        postData.put("cameraPoseMatrix", cameraPoseMatrix);
        postData.put("cameraDisplayOrientedPoseMatrix", cameraDisplayOrientedPoseMatrix);

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

    private String getPointCloudByViewMatrixAsString(float[] viewmtx, FloatBuffer pointCloud) {
        String points3DText = "";

        if(pointCloud == null){
            return "";
        }

        while (pointCloud.hasRemaining()){

            float[] res = new float[4];
            float[] point3D = new float[4];

            float x = pointCloud.get();
            float y = pointCloud.get();
            float z = pointCloud.get();
            float c = pointCloud.get(); //need this to move on!

            point3D[0] = x;
            point3D[1] = y;
            point3D[2] = z;
            point3D[3] = 1.f;

            Matrix.multiplyMV(res, 0, viewmtx, 0, point3D, 0);

            points3DText = points3DText.concat(res[0] + " " + res[1] + " " + res[2] + " " + res[3] + "\n");
        }
        return points3DText;
    }

    private String getDebugAnchorPosition(float[] localPoint, Pose pose) {
        float[] worldPoint = pose.inverse().transformPoint(localPoint);

        String x = Float.toString(worldPoint[0]);
        String y = Float.toString(worldPoint[1]);
        String z = Float.toString(worldPoint[2]);

        return x+","+y+","+z;
    }

    private String getCameraCenter(Pose pose) { //verified with matlab - works.
        float[] camera_loc = pose.getTranslation();
        camera_loc = new float[]{camera_loc[0], camera_loc[1], camera_loc[2], 1.f};

        float[] camera_rot = new float[16];
        float[] camera_rot_trans = new float[16];
        pose.extractRotation().toMatrix(camera_rot, 0);

        Matrix.transposeM(camera_rot_trans, 0, camera_rot, 0);

        float[] camera_world_loc = new float[4];
        Matrix.multiplyMV(camera_world_loc, 0, camera_rot_trans, 0, camera_loc, 0);

        camera_world_loc = new float[]{-1f * camera_world_loc[0], -1f * camera_world_loc[1], -1f * camera_world_loc[2]};

        String x = Float.toString(camera_world_loc[0]);
        String y = Float.toString(camera_world_loc[1]);
        String z = Float.toString(camera_world_loc[2]);

        return x+","+y+","+z;
    }

    private String getLocalAxes(Pose pose) {
        float[] x_point = new float[]{0.1f, 0.f, 0.f};
        float[] y_point = new float[]{0.f, 0.1f, 0.f};
        float[] z_point = new float[]{0.f, 0.f, 0.1f};

        float[] x_point_world = pose.transformPoint(x_point);
        float[] y_point_world = pose.transformPoint(y_point);
        float[] z_point_world = pose.transformPoint(z_point);

        String x0 = Float.toString(x_point_world[0]);
        String x1 = Float.toString(x_point_world[1]);
        String x2 = Float.toString(x_point_world[2]);

        String y0 = Float.toString(y_point_world[0]);
        String y1 = Float.toString(y_point_world[1]);
        String y2 = Float.toString(y_point_world[2]);

        String z0 = Float.toString(z_point_world[0]);
        String z1 = Float.toString(z_point_world[1]);
        String z2 = Float.toString(z_point_world[2]);

        return x0+","+x1+","+x2+","+y0+","+y1+","+y2+","+z0+","+z1+","+z2;
    }


    public void sendLocaliseCommand(Camera camera, String frameData, String frameName) throws JSONException {

        String cameraPose = getCameraPoseString(camera.getPose());
        String cameraPoseCamCenter = getCameraCenter(camera.getPose());
        String cameraPoseLocalAxes = getLocalAxes(camera.getPose());

        String cameraDisplayOrientedPose = getCameraPoseString(camera.getDisplayOrientedPose());
        String cameraDisplayOrientedPoseLocalAxes = getLocalAxes(camera.getDisplayOrientedPose());
        String cameraDisplayOrientedPoseCamCenter = getCameraCenter(camera.getDisplayOrientedPose());

        float[] matrix = new float[16];
        camera.getPose().toMatrix(matrix,0);
        String cameraPoseMatrix = getMatrixString(matrix);

        matrix = new float[16];
        camera.getDisplayOrientedPose().toMatrix(matrix,0);
        String cameraDisplayOrientedPoseMatrix = getMatrixString(matrix);

        String debugAnchorPositionForCameraPose =  getDebugAnchorPosition(new float[]{1.f,0.f,0.f}, camera.getPose());
        String debugAnchorPositionForDisplayOrientedPose =  getDebugAnchorPosition(new float[]{1.f,0.f,0.f}, camera.getDisplayOrientedPose());

        JSONObject postData = new JSONObject();
        postData.put("cameraPose", cameraPose);
        postData.put("cameraDisplayOrientedPose", cameraDisplayOrientedPose);
        postData.put("frameString", frameData);
        postData.put("cameraPoseLocalAxes", cameraPoseLocalAxes);
        postData.put("cameraDisplayOrientedPoseLocalAxes", cameraDisplayOrientedPoseLocalAxes);
        postData.put("cameraPoseCamCenter", cameraPoseCamCenter);
        postData.put("cameraDisplayOrientedPoseCamCenter", cameraDisplayOrientedPoseCamCenter);
        postData.put("debugAnchorPositionForCameraPose", debugAnchorPositionForCameraPose);
        postData.put("debugAnchorPositionForDisplayOrientedPose", debugAnchorPositionForDisplayOrientedPose);
        postData.put("cameraPoseMatrix", cameraPoseMatrix);
        postData.put("cameraDisplayOrientedPoseMatrix", cameraDisplayOrientedPoseMatrix);
        postData.put("frameName", frameName);

        MediaType JSON = MediaType.get("application/json; charset=utf-8");

        Request request = new Request.Builder()
                .url("http://"+IP_ADDRESS+":3000/localise")
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
                    //String foo = responseBody.string();
                    ServerPoints serverPoints = gson.fromJson(responseBody.string(), ServerPoints.class);
                    callBackAction.setServerPoints(serverPoints);


                }
            }
        });
    }

    public void getModel() throws JSONException {

        JSONObject postData = new JSONObject();
        postData.put("command", "getModel");

        MediaType JSON = MediaType.get("application/json; charset=utf-8");

        Request request = new Request.Builder()
                .url("http://"+IP_ADDRESS+":3000/getModel")
                .post(RequestBody.create(postData.toString(), JSON))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {

                    if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                    ServerResponsePoints serverResponsePoints = gson.fromJson(responseBody.string(), ServerResponsePoints.class);
                    callBackAction.updateResultPointCloud(serverResponsePoints);

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


    private String getMatrixString(float[] matrix) {
        String matrixString = matrix[0] + " " + matrix[4] + " " + matrix[8] + " " + matrix[12] + "\n" +
                matrix[1] + " " + matrix[5] + " " + matrix[9] + " " + matrix[13] + "\n" +
                matrix[2] + " " + matrix[6] + " " + matrix[10] + " " + matrix[14] + "\n" +
                matrix[3] + " " + matrix[7] + " " + matrix[11] + " " + matrix[15] + "\n";

        return matrixString;
    }

    public void sendReloadCommand() throws JSONException {

        JSONObject postData = new JSONObject();
        postData.put("command", "reload");

        MediaType JSON = MediaType.get("application/json; charset=utf-8");

        Request request = new Request.Builder()
                .url("http://"+IP_ADDRESS+":3000/reload")
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
}
