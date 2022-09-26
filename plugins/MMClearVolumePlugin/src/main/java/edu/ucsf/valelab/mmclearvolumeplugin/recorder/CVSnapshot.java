
package edu.ucsf.valelab.mmclearvolumeplugin.recorder;

import clearvolume.renderer.cleargl.recorder.VideoRecorderBase;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GLAutoDrawable;
import ij.ImagePlus;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Use the ClearVolume VideRecorder interface to grab a single
 * image and convert it into a Java AWT RGBA image.  Display as an ImageJ RGB image.
 *
 * @author nico
 */
public class CVSnapshot extends VideoRecorderBase {

   @Override
   public boolean screenshot(GLAutoDrawable pGLAutoDrawable,
           boolean pAsynchronous) {

      if (!super.screenshot(pGLAutoDrawable, pAsynchronous)) {
         return false;
      }

      super.toggleActive();

      final int lWidth = pGLAutoDrawable.getSurfaceWidth();
      final int lHeight = pGLAutoDrawable.getSurfaceHeight();

      ByteBuffer lByteBuffer
              = ByteBuffer.allocateDirect(lWidth * lHeight * 3)
              .order(ByteOrder.nativeOrder());

      final GL lGL = pGLAutoDrawable.getGL();

      lGL.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);

      lGL.glReadPixels(0,     // GLint x
              0,              // GLint y
              lWidth,         // GLsizei width
              lHeight,        // GLsizei height
              GL.GL_RGB,      // GLenum format
              GL.GL_UNSIGNED_BYTE, // GLenum type
              lByteBuffer);   // GLvoid *pixels

      mLastImageTimePoint = System.nanoTime();

      BufferedImage lBufferedImage = RecorderUtils.makeBufferedImage(
              lWidth, lHeight, lByteBuffer);

      ImagePlus ip = new ImagePlus("3D", lBufferedImage);
      ip.show();

      return true;
   } 
  
}