package com.google.ar.core.examples.java.common.rendering;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import com.google.ar.core.PointCloud;
import java.io.IOException;
import java.nio.FloatBuffer;

/** Renders a model from the server. */
public class ServerModelCloudRenderer {
  private static final String TAG = ServerModelCloudRenderer.class.getSimpleName();

  // Shader names.
  private static final String VERTEX_SHADER_NAME = "shaders/server_model.vert";
  private static final String FRAGMENT_SHADER_NAME = "shaders/server_model.frag";

  private static final int BYTES_PER_FLOAT = Float.SIZE / 8;
  private static final int FLOATS_PER_POINT = 4; // X,Y,Z,homogeneous.
  private static final int BYTES_PER_POINT = BYTES_PER_FLOAT * FLOATS_PER_POINT;
  private static final int INITIAL_BUFFER_POINTS = 1000;

  private int vbo;
  private int vboSize;

  private int programName;
  private int positionAttribute;
  private int modelViewProjectionUniform;
  private int colorUniform;
  private int pointSizeUniform;

  private int numPoints = 0;

  public ServerModelCloudRenderer() {}

  /**
   * Allocates and initializes OpenGL resources needed by the server model renderer. Must be called on the
   * OpenGL thread, typically in {@link GLSurfaceView.Renderer#onSurfaceCreated(GL10, EGLConfig)}.
   *
   * @param context Needed to access shader source.
   */
  public void createOnGlThread(Context context) throws IOException {
    ShaderUtil.checkGLError(TAG, "before create");

    int[] buffers = new int[3];
    GLES20.glGenBuffers(3, buffers, 0);
    vbo = buffers[0];
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);

    vboSize = INITIAL_BUFFER_POINTS * BYTES_PER_POINT;
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vboSize, null, GLES20.GL_DYNAMIC_DRAW);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

    ShaderUtil.checkGLError(TAG, "buffer alloc");

    int vertexShader =
            ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_NAME);
    int passthroughShader =
            ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME);

    programName = GLES20.glCreateProgram();
    GLES20.glAttachShader(programName, vertexShader);
    GLES20.glAttachShader(programName, passthroughShader);
    GLES20.glLinkProgram(programName);
    GLES20.glUseProgram(programName);

    ShaderUtil.checkGLError(TAG, "program");

    positionAttribute = GLES20.glGetAttribLocation(programName, "a_Position");
    colorUniform = GLES20.glGetUniformLocation(programName, "u_Color");
    modelViewProjectionUniform = GLES20.glGetUniformLocation(programName, "u_ModelViewProjection");
    pointSizeUniform = GLES20.glGetUniformLocation(programName, "u_PointSize");

    ShaderUtil.checkGLError(TAG, "program  params");
  }

  public void update(FloatBuffer fb) {

    ShaderUtil.checkGLError(TAG, "before update");

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);

    // If the VBO is not large enough to fit the new point cloud, resize it.
    numPoints = fb.remaining() / FLOATS_PER_POINT;
    System.out.println("numPoints: " + numPoints);
    if (numPoints * BYTES_PER_POINT > vboSize) {
      while (numPoints * BYTES_PER_POINT > vboSize) {
        vboSize *= 2;
      }
      GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vboSize, null, GLES20.GL_DYNAMIC_DRAW);
    }
    GLES20.glBufferSubData(
            GLES20.GL_ARRAY_BUFFER, 0, numPoints * BYTES_PER_POINT, fb);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

    ShaderUtil.checkGLError(TAG, "after update");
  }

  /**
   * Renders the point cloud. ARCore point cloud is given in world space.
   * @param cameraView the camera view matrix for this frame, typically from {@link
   *     com.google.ar.core.Camera#getViewMatrix(float[], int)}.
   * @param cameraPerspective the camera projection matrix for this frame, typically from {@link
   *     com.google.ar.core.Camera#getProjectionMatrix(float[], int, float, float)}.
   * @param serverPoseMatrix
   * @param mobilePoseMatrix
   */
  public void draw(float[] cameraView, float[] cameraPerspective, float[] serverPoseMatrix, float[] mobilePoseMatrix) {

    float[] firstTerm = new float[16];
    float[] secondTerm = new float[16];
    float[] thirdTerm = new float[16];
    float[] fourthTerm = new float[16];
    float[] fifthTerm = new float[16];
    float[] scaleTerm = new float[]{0.06f, 0.f, 0.f, 0.f, 0.f, 0.06f, 0.f, 0.f, 0.f, 0.f, 0.06f, 0.f, 0.f, 0.f, 0.f, 1.f};
    float[] serverToARCoreCameraSpace = new float[]{1.f, 0.f, 0.f, 0.f, 0.f, -1.f, 0.f, 0.f, 0.f, 0.f, -1.f, 0.f, 0.f, 0.f, 0.f, 1.f};
    float[] scaledServerToARCoreCameraSpace = new float[16];
    float[] rotate90Z = new float[]{0.f, -1.f, 0.f, 0.f, 1.f, 0.f, 0.f, 0.f, 0.f, 0.f, 1.f, 0.f, 0.f, 0.f, 0.f, 1.f};
    float[] modelViewProjection = new float[16];

//    Matrix.multiplyMM(scaledServerToARCoreCameraSpace, 0, scaleTerm, 0, serverToARCoreCameraSpace, 0);
//
//    Matrix.multiplyMM(firstTerm, 0, scaledServerToARCoreCameraSpace, 0, serverPoseMatrix, 0);
//    Matrix.multiplyMM(secondTerm, 0, rotate90Z, 0, firstTerm, 0);
//    Matrix.multiplyMM(thirdTerm, 0, mobilePoseMatrix, 0, secondTerm, 0);
//    Matrix.multiplyMM(fourthTerm, 0, rotate90Z, 0, thirdTerm, 0);
//    Matrix.multiplyMM(fifthTerm, 0, cameraView, 0, fourthTerm, 0);
//    Matrix.multiplyMM(modelViewProjection, 0, cameraPerspective, 0, fifthTerm, 0);

    Matrix.multiplyMM(modelViewProjection, 0, cameraPerspective, 0, cameraView, 0);

    ShaderUtil.checkGLError(TAG, "Before draw");

    GLES20.glUseProgram(programName);
    GLES20.glEnableVertexAttribArray(positionAttribute);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
    GLES20.glVertexAttribPointer(positionAttribute, 4, GLES20.GL_FLOAT, false, BYTES_PER_POINT, 0);
    GLES20.glUniform4f(colorUniform, 255.0f / 255.0f, 5.0f / 255.0f, 5.0f / 255.0f, 1.0f);
    GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjection, 0);
    GLES20.glUniform1f(pointSizeUniform, 30.0f);

    GLES20.glDrawArrays(GLES20.GL_POINTS, 0, numPoints);
    GLES20.glDisableVertexAttribArray(positionAttribute);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

    ShaderUtil.checkGLError(TAG, "Draw");
  }
}
