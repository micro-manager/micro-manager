package edu.ucsf.valelab.mmclearvolumeplugin.recorder;

import clearvolume.renderer.cleargl.recorder.VideoRecorderBase;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GLAutoDrawable;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ColorProcessor;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** @author nico */
public class CVVideoRecorder extends VideoRecorderBase {

  ImageStack is_;

  @Override
  public boolean screenshot(GLAutoDrawable pGLAutoDrawable, boolean pAsynchronous) {

    if (!super.screenshot(pGLAutoDrawable, pAsynchronous)) {
      return false;
    }

    final int lWidth = pGLAutoDrawable.getSurfaceWidth();
    final int lHeight = pGLAutoDrawable.getSurfaceHeight();

    ByteBuffer lByteBuffer =
        ByteBuffer.allocateDirect(lWidth * lHeight * 3).order(ByteOrder.nativeOrder());

    final GL lGL = pGLAutoDrawable.getGL();

    lGL.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);

    lGL.glReadPixels(
        0, // GLint x
        0, // GLint y
        lWidth, // GLsizei width
        lHeight, // GLsizei height
        GL.GL_RGB, // GLenum format
        GL.GL_UNSIGNED_BYTE, // GLenum type
        lByteBuffer); // GLvoid *pixels

    mLastImageTimePoint = System.nanoTime();

    BufferedImage lBufferedImage = RecorderUtils.makeBufferedImage(lWidth, lHeight, lByteBuffer);

    if (is_ == null) {
      is_ = new ImageStack(lWidth, lHeight);
    }
    if (is_.getWidth() != lWidth || is_.getHeight() != lHeight) {
      showStack();
      is_ = new ImageStack(lWidth, lHeight);
    }
    is_.addSlice(new ColorProcessor(lBufferedImage));

    return true;
  }

  public void stopRecording() {
    super.toggleActive();
    if (is_ != null) {
      showStack();
    }
  }

  private void showStack() {
    ImagePlus ip = new ImagePlus("3D", is_);
    ip.show();
    is_ = null;
  }
}
