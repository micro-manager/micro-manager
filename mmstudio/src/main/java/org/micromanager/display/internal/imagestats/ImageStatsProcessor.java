// Copyright (C) 2017 Open Imaging, Inc.
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.display.internal.imagestats;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.histogram.BinMapper1d;
import net.imglib2.histogram.Histogram1d;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.iterator.ZeroMinIntervalIterator;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import org.micromanager.data.Image;
import org.micromanager.display.internal.imagestats.ImagesAndStats.ImageStats;
import org.micromanager.internal.utils.ThreadFactoryFactory;
import org.micromanager.internal.utils.performance.CPUTimer;
import org.micromanager.internal.utils.performance.PerformanceMonitor;
import org.micromanager.internal.utils.performance.WallTimer;

/**
 *
 * @author Mark A. Tsuchida
 */
final class ImageStatsProcessor implements ThrottledProcessQueue.Processor<
      ImageStatsRequest, ImagesAndStats>
{
   private final ExecutorService executor_;

   private PerformanceMonitor perfMon_;

   static ImageStatsProcessor create() {
      return new ImageStatsProcessor();
   }

   private ImageStatsProcessor() {
      // Allow as many threads as requested jobs, since there should be no more
      // than a handful of channels.
      executor_ = new ThreadPoolExecutor(1, Integer.MAX_VALUE,
            60, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
            ThreadFactoryFactory.createThreadFactory("ImageStatsProcessor"));
   }

   void shutdown() {
      executor_.shutdown();
   }

   void setPerformanceMonitor(PerformanceMonitor perfMon) {
      perfMon_ = perfMon;
   }

   @Override
   public ImagesAndStats process(final ImageStatsRequest request)
         throws InterruptedException
   {
      WallTimer timer = WallTimer.createStarted();

      ImageStats[] results = new ImageStats[request.getNumberOfImages()];
      List<Future<ImageStats>> futures = new ArrayList<Future<ImageStats>>();
      for (int i = 0; i < request.getNumberOfImages(); ++i) {
         final Image image = request.getImage(i);
         futures.add(executor_.submit(new Callable<ImageStats>() {
            @Override
            public ImageStats call() throws Exception {
               return computeStats(image, request);
            }
         }));
      }

      for (int i = 0; i < request.getNumberOfImages(); ++i) {
         try {
            results[i] = futures.get(i).get();
         }
         catch (ExecutionException ex) {
            // This could contain a ClassCastException from computeStats() in
            // case the image has unexpected format. In any case, we are only
            // computing stats for display purposes, so should continue if
            // results are unavailable.
            results[i] = null;
         }
      }

      if (perfMon_ != null) {
         perfMon_.sample("Process wall time (ms)", timer.getMs());
         perfMon_.sampleTimeInterval("Process");
      }

      return ImagesAndStats.create(request, results);
   }

   private ImageStats computeStats(Image image,
         ImageStatsRequest request)
         throws ClassCastException
   {
      CPUTimer cpuTimer = CPUTimer.createStarted();

      int nComponents = image.getNumComponents();
      Integer boxedBitDepth = image.getMetadata().getBitDepth();
      int bytesPerSample = image.getBytesPerPixel() / nComponents;
      int bitDepth = boxedBitDepth == null ?
            8 * bytesPerSample : boxedBitDepth;
      int binCountPowerOf2 =
            Math.min(bitDepth, request.getMaxBinCountPowerOf2());

      // RGB888 images can come with an extra component in the pixel buffer.
      // TODO XXX Handle this case!

      ImageStats result = null;
      if (bytesPerSample == 1) {
         Img<UnsignedByteType> img =
               ArrayImgs.unsignedBytes((byte[]) image.getRawPixels(),
                     image.getHeight(), image.getWidth(), nComponents);
         result = compute(applyROIToIterable(img, request.getROIRect(),
               request.getROIMask()),
               nComponents, 8 * bytesPerSample, binCountPowerOf2);
      }
      else if (bytesPerSample == 2) {
         Img<UnsignedShortType> img =
               ArrayImgs.unsignedShorts((short[]) image.getRawPixels(),
                     image.getHeight(), image.getWidth(), nComponents);
         result = compute(applyROIToIterable(img, request.getROIRect(),
               request.getROIMask()),
               nComponents, 8 * bytesPerSample, binCountPowerOf2);
      }

      if (perfMon_ != null) {
         perfMon_.sample("Compute image stats CPU time (ms)", cpuTimer.getMs());
      }

      return result; // null if we don't know how to compute (TODO FIX)
   }

   private <T extends IntegerType<T>> ImageStats compute(Iterable<T> img,
         int numberOfComponents, int sampleBitDepth, int binCountPowerOf2)
   {
      BinMapper1d<T> binMapper =
            PowerOf2BinMapper.create(sampleBitDepth, binCountPowerOf2);

      // Some ugliness to allow us to compute stats for multiple components in
      // a single interation over the pixels.
      List<Histogram1d<T>> histograms = new ArrayList<Histogram1d<T>>();
      long[] counts = new long[numberOfComponents];
      long[] minima = new long[numberOfComponents];
      long[] maxima = new long[numberOfComponents];
      long[] sums = new long[numberOfComponents];
      long[] sumsOfSquares = new long[numberOfComponents];
      for (int component = 0; component < numberOfComponents; ++component) {
         histograms.add(new Histogram1d<T>(binMapper));
         counts[component] = 0;
         minima[component] = Long.MAX_VALUE;
         maxima[component] = Long.MIN_VALUE;
         sums[component] = 0;
         sumsOfSquares[component] = 0;
      }

      // Note: sums of squares could overflow with a huge image (65k by 65k or
      // greater). If we ever deal with such images, we should split the image
      // before computing partial statistics.
      // The reason for computing sums of squares, rather than a running stdev,
      // is to make it easy to parallelize these computations within a single
      // image.

      // Perform the actual computations:
      Iterator<T> iter = img.iterator();
      for (int i = 0; iter.hasNext(); ++i) {
         int component = i % numberOfComponents;
         T sample = iter.next();
         long value = sample.getIntegerLong();

         histograms.get(component).increment(sample);
         counts[component]++;
         if (value < minima[component]) {
            minima[component] = value;
         }
         if (value > maxima[component]) {
            maxima[component] = value;
         }
         sums[component] += value;
         sumsOfSquares[component] += value * value;
      }

      ComponentStats[] componentStats =
            new ComponentStats[numberOfComponents];
      for (int component = 0; component < numberOfComponents; ++component) {
         componentStats[component] = ComponentStats.builder().
               histogram(histograms.get(component).toLongArray(),
                     Math.max(0, sampleBitDepth - binCountPowerOf2)).
               pixelCount(counts[component]).
               minimum(minima[component]).
               maximum(maxima[component]).
               sum(sums[component]).
               sumOfSquares(sumsOfSquares[component]).
               build();
      }

      return ImageStats.create(componentStats);
   }

   private <T extends IntegerType<T>> Iterable<T> applyROIToIterable(
         Img<T> fullImg, Rectangle roiRect, byte[] roiMask)
   {
      if (roiRect == null && roiMask == null) {
         return fullImg;
      }
      else if (roiMask != null) {
         long[] dims = new long[2];
         fullImg.dimensions(dims);
         Img<UnsignedByteType> maskImg = ArrayImgs.unsignedBytes(roiMask, dims);
         return new MaskedIterable<T>(fullImg, maskImg);
      }
      else {
         @SuppressWarnings("null") // We know roiRect != null here
         Interval roiInterval = Intervals.createMinSize(
               roiRect.x, roiRect.width, roiRect.y, roiRect.height);
         return new IntervalView(fullImg, roiInterval);
      }
   }

   // This is a kludge for computing the stats for a masked region. Should be
   // updated to use ImgLib2 ROI API once it's available.
   private static final class MaskedIterable<T> implements Iterable<T> {
      private final Img<T> img_;
      private final Img<UnsignedByteType> mask_;

      private final class MaskIterator implements Iterator<T> {
         // This iterator is maintained pointing to the _next_ position within
         // the mask
         private final ZeroMinIntervalIterator iter_;
         private final RandomAccess<T> imgAccess_;
         private final RandomAccess<UnsignedByteType> maskAccess_;

         private MaskIterator() {
            iter_ = new ZeroMinIntervalIterator(img_);
            imgAccess_ = img_.randomAccess();
            maskAccess_ = mask_.randomAccess();
            skipToNextInMask();
         }

         private void skipToNextInMask() {
            while (iter_.hasNext()) {
               maskAccess_.setPosition(iter_);
               if (maskAccess_.get().get() != 0) {
                  break;
               }
            }
         }

         @Override
         public boolean hasNext() {
            return iter_.hasNext() && maskAccess_.get().get() != 0;
         }

         @Override
         public T next() {
            imgAccess_.setPosition(iter_);
            T ret = imgAccess_.get();
            skipToNextInMask();
            return ret;
         }

         @Override
         public void remove() {
         }
      }

      private MaskedIterable(Img<T> img, Img<UnsignedByteType> mask) {
         img_ = img;
         mask_ = mask;
      }

      @Override
      public Iterator iterator() {
         return new MaskIterator();
      }
   }
}
