/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.helloar;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.opengl.GLES20;
import android.opengl.GLException;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.Toast;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Config;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.helpers.TapHelper;
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper;
import com.google.ar.core.examples.java.common.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer;
import com.google.ar.core.examples.java.common.rendering.PointCloudRenderer;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import mehdi.sakout.fancybuttons.FancyButton;
import okhttp3.OkHttpClient;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3d model of the Android robot.
 */
public class HelloArActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
  private static final String TAG = HelloArActivity.class.getSimpleName();

  // Rendering. The Renderers are created here, and initialized when the GL surface is created.
  private GLSurfaceView surfaceView;

  private boolean installRequested;

  private Session session;
  private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
  private DisplayRotationHelper displayRotationHelper;
  private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
  private TapHelper tapHelper;

  private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
  private final ObjectRenderer virtualObject = new ObjectRenderer();
  private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();

  // Temporary matrix allocated here to reduce number of allocations for each frame.
  private final float[] anchorMatrix = new float[16];
  private static final float[] DEFAULT_COLOR = new float[] {0f, 0f, 0f, 0f};

  private static final String SEARCHING_PLANE_MESSAGE = "Searching for surfaces...";

  private int glViewportWidth = 0;
  private int glViewportHeight = 0;

  private OkHttpClient client;
  private static final String IP_ADDRESS = "138.38.173.225";
  private long startTime = 0;
  private static final int TIME_DELAY = 300;
  private static final int ANCHORS_LIMIT = 9;
  private ArrayList<Point3D> points3D = new ArrayList<>();
  private boolean isSaving = false;
  private TextView cameraIntrinsicsTextView;
  private static final float RADIANS_TO_DEGREES = (float) (180 / Math.PI);
  private static final String DEBUG_TEXT_FORMAT =
                          "Correspondences: %d\n" +
                          "CPU Correspondences: %d\n" +
                          "Saving Frames: %s\n" +
                          "Keyframes Saved: %d\n";
  private FancyButton saveKeyFramesButton;
  private int numberOfKeyframesSaved = 0;

  // Anchors created from taps used for object placing with a given color.
  private static class ColoredAnchor {
    public final Anchor anchor;
    public final float[] color;

    public ColoredAnchor(Anchor a, float[] color4f) {
      this.anchor = a;
      this.color = color4f;
    }
  }

  private final ArrayList<ColoredAnchor> anchors = new ArrayList<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    surfaceView = findViewById(R.id.surfaceview);
    displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

    // Set up tap listener.
    tapHelper = new TapHelper(/*context=*/ this);
    surfaceView.setOnTouchListener(tapHelper);

    // Set up renderer.
    surfaceView.setPreserveEGLContextOnPause(true);
    surfaceView.setEGLContextClientVersion(2);
    surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
    surfaceView.setRenderer(this);
    surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    surfaceView.setWillNotDraw(false);

    cameraIntrinsicsTextView = findViewById(R.id.camera_intrinsics_view);
    saveKeyFramesButton = findViewById(R.id.btn_saveKeyFrames);

    saveKeyFramesButton.setOnClickListener( v -> {
      if(isSaving){
        isSaving = false;
        saveKeyFramesButton.setText("Start Saving Keyframes");
        updateStatusTextView(0,0, isSaving, numberOfKeyframesSaved);
      }else {
        saveKeyFramesButton.setText("Stop Saving Keyframes");
        isSaving = true;
      }
    });

    installRequested = false;

    client = new OkHttpClient.Builder()
            .connectTimeout(50, TimeUnit.SECONDS)
            .writeTimeout(50, TimeUnit.SECONDS)
            .readTimeout(70, TimeUnit.SECONDS)
            .build();

    startTime = System.currentTimeMillis();
    deleteFiles("data_ar");
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (session == null) {
      Exception exception = null;
      String message = null;
      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this);
          return;
        }

        // Create the session.
        session = new Session(/* context= */ this);

        List<CameraConfig> cameraConfigs = session.getSupportedCameraConfigs();
        Config config = new Config(session);
        config.setFocusMode(Config.FocusMode.AUTO);
        session.configure(config);

      } catch (UnavailableArcoreNotInstalledException
          | UnavailableUserDeclinedInstallationException e) {
        message = "Please install ARCore";
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        message = "Please update ARCore";
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        message = "Please update this app";
        exception = e;
      } catch (UnavailableDeviceNotCompatibleException e) {
        message = "This device does not support AR";
        exception = e;
      } catch (Exception e) {
        message = "Failed to create AR session";
        exception = e;
      }

      if (message != null) {
        messageSnackbarHelper.showError(this, message);
        Log.e(TAG, "Exception creating session", exception);
        return;
      }
    }

    // Note that order matters - see the note in onPause(), the reverse applies here.
    try {
      session.resume();
    } catch (CameraNotAvailableException e) {
      // In some cases (such as another camera app launching) the camera may be given to
      // a different app instead. Handle this properly by showing a message and recreate the
      // session at the next iteration.
      messageSnackbarHelper.showError(this, "Camera not available. Please restart the app.");
      session = null;
      return;
    }

    surfaceView.onResume();
    displayRotationHelper.onResume();

    //initial start
    updateStatusTextView(0, 0, isSaving, numberOfKeyframesSaved);
  }

  @Override
  public void onPause() {
    super.onPause();
    if (session != null) {
      // Note that the order matters - GLSurfaceView is paused first so that it does not try
      // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
      // still call session.update() and get a SessionPausedException.
      displayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
          .show();
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

    // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
    try {
      // Create the texture and pass it to ARCore session to be filled during update().
      backgroundRenderer.createOnGlThread(/*context=*/ this);
      pointCloudRenderer.createOnGlThread(/*context=*/ this);
      virtualObject.createOnGlThread(/*context=*/ this, "models/andy.obj", "models/andy.png");
      virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

    } catch (IOException e) {
      Log.e(TAG, "Failed to read an asset file", e);
    }
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
    GLES20.glViewport(0, 0, width, height);
    glViewportWidth = width;
    glViewportHeight = height;
  }

  //insert here the timing appoach that Mark suggested - compare times before and after and send every 500 ms
  @Override
  public void onDrawFrame(GL10 gl) {

    long nowTime = System.currentTimeMillis();

    // Clear screen to notify driver it should not load any pixels from previous frame.
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    if (session == null) {
      return;
    }
    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    displayRotationHelper.updateSessionIfNeeded(session);

    try {

      session.setCameraTextureName(backgroundRenderer.getTextureId());

      // Obtain the current frame from ARSession. When the configuration is set to
      // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
      // camera framerate.
      Frame frame = session.update();
      Camera camera = frame.getCamera();

      // If frame is ready, render camera preview image to the GL surface.
      backgroundRenderer.draw(frame);

      // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
      trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

      // If not tracking, don't draw 3D objects, show tracking failure reason instead.
      if (camera.getTrackingState() == TrackingState.PAUSED) {
        messageSnackbarHelper.showMessage(
            this, TrackingStateHelper.getTrackingFailureReasonString(camera));
        writeTrackingState();
        return;
      }

      // Get projection matrix.
      float[] projmtx = new float[16];
      camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

      // Get camera matrix and draw.
      float[] viewmtx = new float[16];
      camera.getViewMatrix(viewmtx, 0);

      // Compute lighting from average intensity of the image.
      // The first three components are color scaling factors.
      // The last one is the average pixel intensity in gamma space.
      final float[] colorCorrectionRgba = new float[4];
      frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

      // Visualize tracked points.
      // Use try-with-resources to automatically release the point cloud.
      try (PointCloud pointCloud = frame.acquirePointCloud()) {

        FloatBuffer pointCloudCopy = pointCloud.getPoints().duplicate();
        FloatBuffer pointCloudAnchors = pointCloud.getPoints().duplicate();

        pointCloudRenderer.update(pointCloud); // this "uses" up the pointcloud
        pointCloudRenderer.draw(viewmtx, projmtx);

        // Handle one tap per frame.
        handleTap(camera, pointCloudAnchors);

        long elapsedTime = nowTime - startTime;
        if(elapsedTime > TIME_DELAY && isSaving) {

          saveData(pointCloudCopy,viewmtx,projmtx,frame,camera);
          startTime = System.currentTimeMillis();
        }
      }

      float scaleFactor = 0.0010f;
      for (ColoredAnchor coloredAnchor : anchors) {
        if (coloredAnchor.anchor.getTrackingState() != TrackingState.TRACKING) {
          continue;
        }
        // Get the current pose of an Anchor in world space. The Anchor pose is updated
        // during calls to session.update() as ARCore refines its estimate of the world.
        coloredAnchor.anchor.getPose().toMatrix(anchorMatrix, 0);

        // Update and draw the model and its shadow.
        virtualObject.updateModelMatrix(anchorMatrix, scaleFactor);
        virtualObject.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
      }

    } catch (Throwable t) {
      // Avoid crashing the application due to unhandled exceptions.
      Log.e(TAG, "Exception on the OpenGL thread", t);
    }


  }

  private void saveData(FloatBuffer pointCloudCopy, float[] viewmtx, float[] projmtx, Frame frame, Camera camera) throws NotYetAvailableException {
    Long tsLong = System.currentTimeMillis();
    String timestamp = tsLong.toString();

    System.out.println("Saving at:" + timestamp);

    ArrayList<double[]> correspondences = get2D3DCorrespondences(pointCloudCopy, viewmtx, projmtx);
    ArrayList<double[]> cpuImageCorrespondences = getCPUImageCorrespondences(frame, correspondences);

    if(correspondences.size() > 4 && anchors.isEmpty()) {
      Image frameImage = null;
      try {

        writeIntrinsicsToFile(camera.getImageIntrinsics(), "imageIntrinsics_" + timestamp);
        writeIntrinsicsToFile(camera.getTextureIntrinsics(), "textureIntrinsics_" + timestamp);

        writeLog(correspondences, cpuImageCorrespondences, timestamp);
        writeCorrespondences(cpuImageCorrespondences, "cpuImageCorrespondences_" + timestamp);

        frameImage = frame.acquireCameraImage();
        saveCPUFrameJPEG(frameImage, timestamp);
        numberOfKeyframesSaved++;
        frameImage.close();

        float[] poseOrientedMatrix = new float[16];
        float[] poseMatrix = new float[16];
        float[] poseSensorMatrix = new float[16];

        Pose cameraPoseOriented = camera.getDisplayOrientedPose();
        cameraPoseOriented.toMatrix(poseOrientedMatrix,0);
        writeMatrixToFile(poseOrientedMatrix,"displayOrientedPose_"+timestamp);

        Pose cameraPose = camera.getPose();
        cameraPose.toMatrix(poseMatrix,0);
        writeMatrixToFile(poseMatrix,"cameraPose_"+timestamp);

        Pose sensorPose = frame.getAndroidSensorPose();
        sensorPose.toMatrix(poseSensorMatrix,0);
        writeMatrixToFile(poseSensorMatrix,"sensorPose_"+timestamp);

        updateStatusTextView(correspondences.size(), cpuImageCorrespondences.size(), isSaving, numberOfKeyframesSaved);

      } catch (IOException e) {
        e.printStackTrace();
      }

    }else {
      Image frameImage = null;
      try {

        writeIntrinsicsToFile(camera.getImageIntrinsics(), "imageIntrinsics_after_anchors_" + timestamp);
        writeIntrinsicsToFile(camera.getTextureIntrinsics(), "textureIntrinsics_after_anchors_" + timestamp);

        writeLog(correspondences, cpuImageCorrespondences, timestamp);
        writeCorrespondences(cpuImageCorrespondences, "cpuImageCorrespondences_after_anchors_" + timestamp);

        frameImage = frame.acquireCameraImage();
        saveCPUFrameJPEG(frameImage, timestamp);
        numberOfKeyframesSaved++;
        frameImage.close();

        float[] poseOrientedMatrix = new float[16];
        float[] poseMatrix = new float[16];
        float[] poseSensorMatrix = new float[16];

        Pose cameraPoseOriented = camera.getDisplayOrientedPose();
        cameraPoseOriented.toMatrix(poseOrientedMatrix,0);
        writeMatrixToFile(poseOrientedMatrix,"displayOrientedPose_after_anchors_"+timestamp);

        Pose cameraPose = camera.getPose();
        cameraPose.toMatrix(poseMatrix,0);
        writeMatrixToFile(poseMatrix,"cameraPose_after_anchors_"+timestamp);

        Pose sensorPose = frame.getAndroidSensorPose();
        sensorPose.toMatrix(poseSensorMatrix,0);
        writeMatrixToFile(poseSensorMatrix,"sensorPose_after_anchors_"+timestamp);

        for (ColoredAnchor coloredAnchor : anchors){
          Pose anchorPose = coloredAnchor.anchor.getPose();
          writeMatrixToFile(poseSensorMatrix,"anchor_pose_"+timestamp);
        }

        updateStatusTextView(correspondences.size(), cpuImageCorrespondences.size(), isSaving, numberOfKeyframesSaved);

      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private void addAnchors(FloatBuffer pointCloud){

    float[] objColor = new float[]{0.0f, 0.0f, 255.0f, 255.0f};

    while (pointCloud.hasRemaining()) {

      float x = pointCloud.get();
      float y = pointCloud.get();
      float z = pointCloud.get();
      float c = pointCloud.get(); //just to get the position moving - not used

      if(c >= 0.72f) {
        Pose pose = Pose.makeTranslation(x,y,z);
        Anchor anchor = session.createAnchor(pose);

        if(anchors.size() < ANCHORS_LIMIT) {
          anchors.add(new ColoredAnchor(anchor, objColor));
        }
      }
    }

  }

  // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
  private void handleTap(Camera camera, FloatBuffer pointCloud) throws NotYetAvailableException {
    MotionEvent tap = tapHelper.poll();

    if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
      addAnchors(pointCloud);
    }
  }

  private void updateStatusTextView(int s1, int s2, boolean isRecording, int numberOfKeyframesSaved) {
    runOnUiThread(() -> cameraIntrinsicsTextView.setText(getCameraIntrinsicsText(s1,s2,isRecording, numberOfKeyframesSaved)));
  }

  private void writeIntrinsicsToFile(CameraIntrinsics intrinsics, String filename) throws IOException {
    float[] values = new float[9];
    values[0] = intrinsics.getFocalLength()[0];
    values[1] = 0;
    values[2] = intrinsics.getPrincipalPoint()[0];
    values[3] = 0;
    values[4] = intrinsics.getFocalLength()[1];
    values[5] = intrinsics.getPrincipalPoint()[1];
    values[6] = 0;
    values[7] = 0;
    values[8] = 1;

    String matrixString = values[0] + " " + values[1] + " " + values[2] + "\n" +
                          values[3] + " " + values[4] + " " + values[5] + "\n" +
                          values[6] + " " + values[7] + " " + values[8] + "\n";

    File matrixFile = new File(Environment.getExternalStorageDirectory().toString() + "/data_ar/" + filename + ".txt");
    FileOutputStream outputStream = new FileOutputStream(matrixFile);

    outputStream.write(matrixString.getBytes(Charset.forName("UTF-8")));
    outputStream.flush();
    outputStream.close();
  }

  private void saveCPUFrameJPEG(Image frameImage, String filename) throws IOException {
    byte[] imageData = NV21toJPEG(YUV_420_888toNV21(frameImage),
            frameImage.getWidth(), frameImage.getHeight());

    String path = Environment.getExternalStorageDirectory().toString() + "/data_ar/frame_"+filename+".jpg";
    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(path));
    bos.write(imageData);
    bos.flush();
    bos.close();
  }

  private ArrayList<double[]> getCPUImageCorrespondences(Frame frame, ArrayList<double[]> correspondences) {

    ArrayList<double[]> cpuImageCorrespondences = new ArrayList<>();

    float[] xyVIEW = new float[correspondences.size() * 2];
    float[] xyCPU = new float[correspondences.size() * 2];

    for (int i = 0; i < correspondences.size(); i++) {

      xyVIEW[2*i] = (float) correspondences.get(i)[0];
      xyVIEW[2*i+1] = (float) correspondences.get(i)[1];
    }

    frame.transformCoordinates2d(Coordinates2d.VIEW, xyVIEW, Coordinates2d.IMAGE_PIXELS, xyCPU);

    ArrayList<double[]> tempCPUImageCorrespondenceXY = new ArrayList<>();

    for (int i = 0; i < xyCPU.length; i+=2) {

      double[] cpuImageCorrespondenceXY = new double[]{0,0};

      cpuImageCorrespondenceXY[0] = xyCPU[i];
      cpuImageCorrespondenceXY[1] = xyCPU[i+1];

      tempCPUImageCorrespondenceXY.add(cpuImageCorrespondenceXY);
    }

    for (int i = 0; i < tempCPUImageCorrespondenceXY.size(); i++) {

      double[] cpuImageCorrespondence2D3D = new double[]{0,0,0,0,0};

      cpuImageCorrespondence2D3D[0] = tempCPUImageCorrespondenceXY.get(i)[0];
      cpuImageCorrespondence2D3D[1] = tempCPUImageCorrespondenceXY.get(i)[1];
      cpuImageCorrespondence2D3D[2] = correspondences.get(i)[2];
      cpuImageCorrespondence2D3D[3] = correspondences.get(i)[3];
      cpuImageCorrespondence2D3D[4] = correspondences.get(i)[4];

      cpuImageCorrespondences.add(cpuImageCorrespondence2D3D);

    }

    return cpuImageCorrespondences;
  }

  private ArrayList<double[]> get2D3DCorrespondences(FloatBuffer points3D, float[] viewmtx, float[] projmtx) {

    float x = 0;
    float y = 0;
    float z = 0;
    float w = 1;
    float c = 0; // not used
    float[] point3D = new float[4];
    float[] world2screenMatrix = new float[16];
    float[] ndcPoint = new float[4];

    ArrayList<double[]> correspondences2D3D = new ArrayList<>();

    Matrix.multiplyMM(world2screenMatrix, 0, projmtx, 0, viewmtx, 0);

    while (points3D.hasRemaining()){

      //these two need to be declared here!
      double[] screenPoint = new double[]{0,0};
      double[] correspondence2D3D = new double[]{0,0,0,0,0};

      x = points3D.get();
      y = points3D.get();
      z = points3D.get();
      c = points3D.get(); //just to get the position moving - not used

      point3D[0] = x;
      point3D[1] = y;
      point3D[2] = z;
      point3D[3] = w;

      Matrix.multiplyMV(ndcPoint, 0,  world2screenMatrix, 0,  point3D, 0);

      ndcPoint[0] = ndcPoint[0] / ndcPoint[3];
      ndcPoint[1] = ndcPoint[1] / ndcPoint[3];

      screenPoint[0] = glViewportWidth * ((ndcPoint[0] + 1.0) / 2.0);
      screenPoint[1] = glViewportHeight * ((1.0 - ndcPoint[1]) / 2.0);

      correspondence2D3D[0] = screenPoint[0];
      correspondence2D3D[1] = screenPoint[1];
      correspondence2D3D[2] = point3D[0];
      correspondence2D3D[3] = point3D[1];
      correspondence2D3D[4] = point3D[2];

      correspondences2D3D.add(correspondence2D3D);
    }

    return correspondences2D3D;

  }

  private void writeTrackingState() throws IOException {
    Long tsLong = System.currentTimeMillis();
    String timestamp = tsLong.toString();

    String string = "Tracking PAUSED";
    File file = new File(Environment.getExternalStorageDirectory().toString() + "/data_ar/tracking_state_"+timestamp+".txt");
    FileOutputStream outputStream = new FileOutputStream(file);

    outputStream.write(string.getBytes(Charset.forName("UTF-8")));
    outputStream.flush();
    outputStream.close();
  }

  private void writeCorrespondences(ArrayList<double[]> correspondences, String filename) throws IOException {
    String txt = "";

    for (double[] correspondence : correspondences){
      txt = txt.concat(correspondence[0] + " " + correspondence[1] + " " + correspondence[2] + " " + correspondence[3] + " " + correspondence[4] + "\n");
    }

    File arrayFile = new File(Environment.getExternalStorageDirectory().toString() + "/data_ar/"+filename+".txt");
    FileOutputStream outputStream = new FileOutputStream(arrayFile);

    outputStream.write(txt.getBytes(Charset.forName("UTF-8")));
    outputStream.flush();
    outputStream.close();
  }

  private void writeLog(ArrayList<double[]> correspondences, ArrayList<double[]> cpuImageCorrespondences, String timestamp) throws IOException {

    String txt = correspondences.size() + " " + cpuImageCorrespondences.size();

    File logFile = new File(Environment.getExternalStorageDirectory().toString() + "/data_ar/log_file_"+timestamp+".txt");
    FileOutputStream outputStream = new FileOutputStream(logFile);

    outputStream.write(txt.getBytes(Charset.forName("UTF-8")));
    outputStream.flush();
    outputStream.close();

  }

  public void takeScreenshot(GL10 gl) throws IOException {
    int w = glViewportWidth;
    int h = glViewportHeight;
    int bitmapBuffer[] = new int[w * h];
    int bitmapSource[] = new int[w * h];

    IntBuffer intBuffer = IntBuffer.wrap(bitmapBuffer);
    intBuffer.position(0);

    try {
      gl.glReadPixels(0, 0, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer);
      int offset1, offset2;
      for (int i = 0; i < h; i++) {
        offset1 = i * w;
        offset2 = (h - i - 1) * w;
        for (int j = 0; j < w; j++) {
          int texturePixel = bitmapBuffer[offset1 + j];
          int blue = (texturePixel >> 16) & 0xff;
          int red = (texturePixel << 16) & 0x00ff0000;
          int pixel = (texturePixel & 0xff00ff00) | red | blue;
          bitmapSource[offset2 + j] = pixel;
        }
      }
    } catch (GLException e) {

    }

    String mPath = Environment.getExternalStorageDirectory().toString() + "/data_ar/frame.jpg";
    File imageFile = new File(mPath);
    FileOutputStream outputStream = new FileOutputStream(imageFile);
    int quality = 100;

    Bitmap bitmap = Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888);

    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);

    outputStream.flush();
    outputStream.close();
  }

  private void write3DPoints(FloatBuffer points) throws IOException {
    String txt = "";

    while (points.hasRemaining()){
      txt = txt.concat(Float.toString(points.get()) + " ");
    }

    File matrixFile = new File(Environment.getExternalStorageDirectory().toString() + "/data_ar/points3Dworld.txt");
    FileOutputStream outputStream = new FileOutputStream(matrixFile);

    outputStream.write(txt.getBytes(Charset.forName("UTF-8")));
    outputStream.flush();
    outputStream.close();
  }

  private void writeMatrixToFile(float[] matrix, String filename) throws IOException {
    String matrixString = matrix[0] + " " + matrix[4] + " " + matrix[8] + " " +  matrix[12] + "\n" +
                          matrix[1] + " " + matrix[5] + " " + matrix[9] + " " +  matrix[13] + "\n" +
                          matrix[2] + " " + matrix[6] + " " + matrix[10] + " " + matrix[14] + "\n" +
                          matrix[3] + " " + matrix[7] + " " + matrix[11] + " " + matrix[15] + "\n";

    File matrixFile = new File(Environment.getExternalStorageDirectory().toString() + "/data_ar/" + filename + ".txt");
    FileOutputStream outputStream = new FileOutputStream(matrixFile);

    outputStream.write(matrixString.getBytes(Charset.forName("UTF-8")));
    outputStream.flush();
    outputStream.close();
  }

  private byte[] NV21toJPEG(byte[] nv21, int width, int height) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
    yuv.compressToJpeg(new Rect(0, 0, width, height), 100, out);
    return out.toByteArray();
  }

  private byte[] YUV_420_888toNV21(Image image) {
    byte[] nv21;
    ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
    ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
    ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

    int ySize = yBuffer.remaining();
    int uSize = uBuffer.remaining();
    int vSize = vBuffer.remaining();

    nv21 = new byte[ySize + uSize + vSize];

    //U and V are swapped
    yBuffer.get(nv21, 0, ySize);
    vBuffer.get(nv21, ySize, vSize);
    uBuffer.get(nv21, ySize + vSize, uSize);

    return nv21;
  }

  /** Checks if we detected at least one plane. */
  private boolean hasTrackingPlane() {
    for (Plane plane : session.getAllTrackables(Plane.class)) {
      if (plane.getTrackingState() == TrackingState.TRACKING) {
        return true;
      }
    }
    return false;
  }

  private void deleteFiles(String path) {
    File dir = new File(Environment.getExternalStorageDirectory()+"/"+path);
    if (dir.isDirectory())
    {
      String[] children = dir.list();
      if(children != null) {
        for (int i = 0; i < children.length; i++) {
          new File(dir, children[i]).delete();
        }
      }
    }
  }

  private String getCameraIntrinsicsText(int s1, int s2, boolean isRecording, int numberOfKeyframesSaved) {
    return String.format(DEBUG_TEXT_FORMAT,s1,s2,isRecording, numberOfKeyframesSaved);
  }
}
