package edu.ucsf.valelab.mmclearvolumeplugin.recorder;

import clearvolume.renderer.cleargl.recorder.VideoRecorderBase;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Single-shot OpenGL frame grabber for use by {@link
 * edu.ucsf.valelab.mmclearvolumeplugin.animation.AnimationPlayer}.
 *
 * <p>Each call to {@link #armAndWait(long, TimeUnit)} arms the recorder for
 * exactly one capture, triggers a redraw via the caller, then blocks until the
 * OpenGL render loop fires {@link #screenshot} and delivers the frame.
 *
 * <p>Typical usage per animation frame:
 * <pre>
 *   recorder.armAndWait(2, TimeUnit.SECONDS);
 *   BufferedImage frame = recorder.getLastFrame();
 * </pre>
 */
public class AnimationFrameRecorder extends VideoRecorderBase {

   private volatile CountDownLatch latch_ = new CountDownLatch(0);
   private volatile BufferedImage lastFrame_ = null;

   public AnimationFrameRecorder() {
      super();
      // Disable the rate limiter in the base class by setting a very high
      // target frame rate; we control pacing ourselves.
      setTargetFrameRate(10000);
   }

   /**
    * Arms the recorder to capture the next rendered frame.
    *
    * <p>After calling this method the caller should trigger a redraw
    * (e.g. via {@code viewer.addTranslationZ(0)}) and then call
    * {@link #await(long, TimeUnit)} to block until the frame arrives.
    */
   public void arm() {
      lastFrame_ = null;
      latch_ = new CountDownLatch(1);
      setActive(true);
   }

   /**
    * Blocks until the previously armed capture completes or the timeout
    * elapses.
    *
    * @param timeout  maximum time to wait
    * @param unit     time unit of timeout
    * @return true if a frame was captured within the timeout
    * @throws InterruptedException if the waiting thread is interrupted
    */
   public boolean await(long timeout, TimeUnit unit)
         throws InterruptedException {
      return latch_.await(timeout, unit);
   }

   /**
    * Convenience method: arms the recorder, then immediately blocks until the
    * frame arrives or the timeout elapses.
    *
    * <p>The caller is responsible for triggering a redraw after calling this
    * method (e.g. via {@code viewer.addTranslationZ(0)}).
    *
    * @param timeout  maximum time to wait
    * @param unit     time unit of timeout
    * @return true if a frame was captured within the timeout
    * @throws InterruptedException if the waiting thread is interrupted
    */
   public boolean armAndWait(long timeout, TimeUnit unit)
         throws InterruptedException {
      arm();
      return latch_.await(timeout, unit);
   }

   /**
    * Returns the most recently captured frame, or null if no frame has been
    * captured since the last {@link #armAndWait} call.
    */
   public BufferedImage getLastFrame() {
      return lastFrame_;
   }

   @Override
   public boolean screenshot(GLAutoDrawable pGLAutoDrawable,
                             boolean pAsynchronous) {
      if (!super.screenshot(pGLAutoDrawable, pAsynchronous)) {
         return false;
      }

      // Disarm immediately so only one frame is captured.
      setActive(false);

      final int width = pGLAutoDrawable.getSurfaceWidth();
      final int height = pGLAutoDrawable.getSurfaceHeight();

      ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 3)
            .order(ByteOrder.nativeOrder());

      final GL gl = pGLAutoDrawable.getGL();
      gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
      gl.glReadPixels(0, 0, width, height,
            GL.GL_RGB, GL.GL_UNSIGNED_BYTE, buf);

      mLastImageTimePoint = System.nanoTime();

      lastFrame_ = RecorderUtils.makeBufferedImage(width, height, buf);
      latch_.countDown();
      return true;
   }
}
