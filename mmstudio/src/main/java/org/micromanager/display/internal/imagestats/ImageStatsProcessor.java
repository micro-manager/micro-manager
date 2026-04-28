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

import com.google.common.base.Preconditions;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.histogram.BinMapper1d;
import net.imglib2.histogram.Histogram1d;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.transform.integer.MixedTransform;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.ConstantUtils;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.MixedTransformView;
import net.imglib2.view.Views;
import org.micromanager.data.Image;
import org.micromanager.internal.utils.ThreadFactoryFactory;
import org.micromanager.internal.utils.performance.CPUTimer;
import org.micromanager.internal.utils.performance.PerformanceMonitor;
import org.micromanager.internal.utils.performance.WallTimer;

/**
 * @author Mark A. Tsuchida
 */
public final class ImageStatsProcessor {
   private static final int MASK_THRESH = 128;

   private final ExecutorService executor_;

   private PerformanceMonitor perfMon_;

   public static ImageStatsProcessor create() {
      return new ImageStatsProcessor();
   }

   private ImageStatsProcessor() {
      // Allow as many threads as requested jobs, since there should be no more
      // than a handful of channels.
      executor_ = new ThreadPoolExecutor(1, Integer.MAX_VALUE,
            60, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
            ThreadFactoryFactory.createThreadFactory("ImageStatsProcessor"));
   }

   public void shutdown() {
      executor_.shutdown();
   }

   void setPerformanceMonitor(PerformanceMonitor perfMon) {
      perfMon_ = perfMon;
   }

   public ImagesAndStats process(final long sequenceNumber,
                                 final ImageStatsRequest request,
                                 boolean interruptible)
         throws InterruptedException {
      WallTimer timer = WallTimer.createStarted();

      ImageStats[] results = new ImageStats[request.getNumberOfImages()];
      List<Future<ImageStats>> futures = new ArrayList<Future<ImageStats>>();
      for (int i = 0; i < request.getNumberOfImages(); ++i) {
         final Image image = request.getImage(i);
         final int ii = i;
         futures.add(executor_.submit(new Callable<ImageStats>() {
            @Override
            public ImageStats call() throws Exception {
               return computeStats(image, request, ii);
            }
         }));
      }

      for (int i = 0; i < request.getNumberOfImages(); ++i) {
         try {
            while (results[i] == null) {
               try {
                  results[i] = futures.get(i).get();
               } catch (InterruptedException ie) {
                  if (interruptible) {
                     throw ie;
                  }
               }
            }
         } catch (ExecutionException ex) {
            throw new RuntimeException(ex);
         }
      }

      if (perfMon_ != null) {
         perfMon_.sample("Process wall time (ms)", timer.getMs());
         perfMon_.sampleTimeInterval("Process");
      }

      return ImagesAndStats.create(sequenceNumber, request, results);
   }

   private ImageStats computeStats(Image image,
                                   ImageStatsRequest request, int index)
         throws ClassCastException {
      final CPUTimer cpuTimer = CPUTimer.createStarted();

      final int nComponents = image.getNumComponents();
      final Integer boxedBitDepth = image.getMetadata().getBitDepth();
      final int bytesPerSample = image.getBytesPerPixel() / nComponents;
      final int bitDepth = boxedBitDepth == null
            ? 8 * bytesPerSample : boxedBitDepth;
      final int binCountPowerOf2 =
            Math.min(bitDepth, request.getMaxBinCountPowerOf2());

      // Determine the overlap between the ROI rect/mask and the image
      boolean useROI;
      Rectangle imageBounds = new Rectangle(0, 0, image.getWidth(), image.getHeight());
      Rectangle maskBounds = null;
      byte[] maskBytes = request.getROIMask();
      if (request.getROIBounds() != null) {
         maskBounds = new Rectangle(request.getROIBounds());
         useROI = true;
      } else {
         useROI = false;
      }
      if (maskBounds == null || maskBounds.width == 0 || maskBounds.height == 0) {
         maskBounds = imageBounds;
         maskBytes = null;
         useROI = false;
      }
      Rectangle statsBounds = new Rectangle();
      Rectangle.intersect(imageBounds, maskBounds, statsBounds);
      if (statsBounds.width <= 0 || statsBounds.height <= 0) {
         statsBounds = imageBounds;
         maskBytes = null;
         useROI = false;
      }

      // If (the used part of) the mask has no pixels, revert to full image
      IterableInterval<UnsignedByteType> mask =
            wrapROIMask(maskBytes, nComponents, maskBounds, statsBounds);
      boolean maskEmpty = true;
      for (Cursor<UnsignedByteType> c = mask.cursor(); c.hasNext(); c.fwd()) {
         if (c.get().getInteger() >= MASK_THRESH) {
            maskEmpty = false;
            break;
         }
      }
      if (maskEmpty) {
         statsBounds = imageBounds;
         mask = wrapROIMask(null, nComponents, imageBounds, statsBounds);
         useROI = false;
      }

      ImageStats result = null;
      int width = image.getWidth();
      int height = image.getHeight();
      if (bytesPerSample == 1) {
         RandomAccessibleInterval<UnsignedByteType> img;
         if (nComponents == 3) {
            // Transform BGRA8888 to BGR888 (flipping to RGB is done later)
            img = ArrayImgs.unsignedBytes((byte[]) image.getRawPixels(),
                    4, width, height);
            img = Views.interval(img,
                    Intervals.createMinSize(
                            0, 0, 0,
                            3, width, height));
         } else {
            img = ArrayImgs.unsignedBytes((byte[]) image.getRawPixels(),
                    nComponents, width, height);
         }
         result = compute(
               clipToRect(img, nComponents, statsBounds),
               mask,
               nComponents, boxedBitDepth, bitDepth, binCountPowerOf2,
               useROI, index);
      } else if (bytesPerSample == 2) {
         Img<UnsignedShortType> img =
               ArrayImgs.unsignedShorts((short[]) image.getRawPixels(),
                     nComponents, image.getWidth(), image.getHeight());
         result = compute(
               clipToRect(img, nComponents, statsBounds),
               mask,
               nComponents, boxedBitDepth, bitDepth, binCountPowerOf2,
               useROI, index);
      } else if (bytesPerSample == 4 && nComponents == 1) {
         result = computeFloatStats(image, statsBounds, maskBytes, maskBounds, useROI, index);
      }

      if (perfMon_ != null) {
         perfMon_.sample("Process CPU time (ms)", cpuTimer.getMs());
      }

      return result; // null if we don't know how to compute (TODO FIX)
   }

   private ImageStats computeFloatStats(Image image, java.awt.Rectangle statsBounds,
                                        byte[] maskBytes, java.awt.Rectangle maskBounds,
                                        boolean useROI, int index) {
      float[] pixels = (float[]) image.getRawPixels();
      int width = image.getWidth();
      float fMin = Float.MAX_VALUE;
      float fMax = -Float.MAX_VALUE;
      float fMinNonZero = Float.MAX_VALUE;
      long count = 0;
      long countNonZero = 0;
      double sum = 0.0;
      double sumNonZero = 0.0;
      double sumOfSquares = 0.0;

      // First pass: find min/max, accumulate sum/sumOfSquares/counts
      // (ignoring NaN, Infinity, and masked-out pixels)
      for (int y = statsBounds.y; y < statsBounds.y + statsBounds.height; y++) {
         for (int x = statsBounds.x; x < statsBounds.x + statsBounds.width; x++) {
            if (maskBytes != null) {
               int maskIdx = (y - maskBounds.y) * maskBounds.width + (x - maskBounds.x);
               if ((maskBytes[maskIdx] & 0xff) < MASK_THRESH) {
                  continue;
               }
            }
            float v = pixels[y * width + x];
            if (Float.isNaN(v) || Float.isInfinite(v)) {
               continue;
            }
            count++;
            sum += v;
            sumOfSquares += (double) v * v;
            if (v < fMin) {
               fMin = v;
            }
            if (v > fMax) {
               fMax = v;
            }
            if (v != 0.0f) {
               countNonZero++;
               sumNonZero += v;
               if (v < fMinNonZero) {
                  fMinNonZero = v;
               }
            }
         }
      }
      if (count == 0) {
         fMin = 0.0f;
         fMax = 0.0f;
      }
      if (countNonZero == 0) {
         fMinNonZero = 0.0f;
      }

      // Second pass: build a 256-bin histogram spanning [fMin, fMax].
      // bin width = (fMax - fMin) / N_BINS (floating-point, not power-of-2).
      // Storing rangeMin_ = fMin and binWidthFloat_ in IntegerComponentStats means
      // getQuantile() returns actual pixel values, which are passed directly to the
      // ImageJ FloatProcessor LUT. This works for negative fMin as well.
      final int N_BINS = 256;
      final float range = fMax - fMin;
      // When all pixels are identical (range == 0), use binWidth=0 as a sentinel so
      // that getHistogramRangeMax() returns ceil(fMax) rather than fMin + 256.
      final double fBinWidth = (range == 0.0f) ? 0.0 : (double) range / N_BINS;

      // [0]=underflow, [1..N_BINS]=in-range, [N_BINS+1]=overflow
      long[] hist = new long[N_BINS + 2];

      for (int y = statsBounds.y; y < statsBounds.y + statsBounds.height; y++) {
         for (int x = statsBounds.x; x < statsBounds.x + statsBounds.width; x++) {
            if (maskBytes != null) {
               int maskIdx = (y - maskBounds.y) * maskBounds.width + (x - maskBounds.x);
               if ((maskBytes[maskIdx] & 0xff) < MASK_THRESH) {
                  continue;
               }
            }
            float v = pixels[y * width + x];
            if (Float.isNaN(v) || Float.isInfinite(v)) {
               continue;
            }
            int bin;
            if (range == 0.0f) {
               bin = 1;
            } else {
               bin = 1 + (int) Math.floor((v - fMin) / fBinWidth);
               bin = Math.max(1, Math.min(N_BINS, bin));
            }
            hist[bin]++;
         }
      }

      // sum and sumOfSquares are stored as long in IntegerComponentStats.
      // Round to the nearest integer so that getMeanIntensity() and
      // getStandardDeviation() return values that are correct to integer precision,
      // matching the display which shows mean and stdev rounded to integers.
      IntegerComponentStats stats = IntegerComponentStats.builder()
            .histogram(hist, 0)
            .isFloat(true)
            .rangeMin((double) fMin)
            .binWidthFloat(fBinWidth)
            .pixelCount(count)
            .pixelCountExcludingZeros(countNonZero)
            .usedROI(useROI)
            .minimum((long) Math.floor(fMin))
            .minimumExcludingZeros((long) Math.floor(fMinNonZero))
            .maximum((long) Math.ceil(fMax))
            .sum(Math.round(sum))
            .sumOfSquares(Math.round(sumOfSquares))
            .build();
      return ImageStats.create(index, stats);
   }

   private <T extends IntegerType<T>> ImageStats compute(
         IterableInterval<T> img, IterableInterval<UnsignedByteType> mask,
         int nComponents, Integer metadataBitDepth, int sampleBitDepth,
         int binCountPowerOf2, boolean isROI, int index) {
      // It's easier to debug if we check first...
      Preconditions.checkArgument(img.numDimensions() == 3);
      Preconditions.checkArgument(img.dimension(0) == nComponents);
      for (int d = 0; d < 3; ++d) {
         Preconditions.checkArgument(img.dimension(d) == mask.dimension(d));
      }

      BinMapper1d<T> binMapper =
            PowerOf2BinMapper.create(sampleBitDepth, binCountPowerOf2);

      // Some ugliness to allow us to compute stats for multiple components in
      // a single interation over the pixels.
      List<Histogram1d<T>> histograms = new ArrayList<Histogram1d<T>>();
      long[] counts = new long[nComponents];
      long[] countsExcludingZeros = new long[nComponents];
      long[] minima = new long[nComponents];
      long[] minimaExcludingZeros = new long[nComponents];
      long[] maxima = new long[nComponents];
      long[] sums = new long[nComponents];
      long[] sumsOfSquares = new long[nComponents];
      for (int component = 0; component < nComponents; ++component) {
         histograms.add(new Histogram1d<T>(binMapper));
         counts[component] = 0;
         countsExcludingZeros[component] = 0;
         minima[component] = Long.MAX_VALUE;
         minimaExcludingZeros[component] = Long.MAX_VALUE;
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
      Cursor<T> dataCursor = img.localizingCursor();
      Cursor<UnsignedByteType> maskCursor = mask.cursor();
      while (dataCursor.hasNext()) {
         T dataSample = dataCursor.next();
         UnsignedByteType maskSample = maskCursor.next();
         if (maskSample.getInteger() < MASK_THRESH) {
            continue;
         }

         // Flip component index to view BGR as RGB
         int component = nComponents - 1 - dataCursor.getIntPosition(0);

         long dataValue = dataSample.getIntegerLong();

         histograms.get(component).increment(dataSample);
         counts[component]++;
         if (dataValue > 0) {
            countsExcludingZeros[component]++;
         }
         if (dataValue < minima[component]) {
            minima[component] = dataValue;
         }
         if (dataValue < minimaExcludingZeros[component] && dataValue > 0) {
            minimaExcludingZeros[component] = dataValue;
         }
         if (dataValue > maxima[component]) {
            maxima[component] = dataValue;
         }
         sums[component] += dataValue;
         sumsOfSquares[component] += dataValue * dataValue;
      }

      IntegerComponentStats[] componentStats =
            new IntegerComponentStats[nComponents];
      for (int component = 0; component < nComponents; ++component) {
         componentStats[component] = IntegerComponentStats.builder()
               .bitDepth(metadataBitDepth)
               .histogram(histograms.get(component).toLongArray(),
                     Math.max(0, sampleBitDepth - binCountPowerOf2))
               .pixelCount(counts[component])
               .pixelCountExcludingZeros(countsExcludingZeros[component])
               .usedROI(isROI)
               .minimum(minima[component])
               .minimumExcludingZeros(minimaExcludingZeros[component])
               .maximum(maxima[component])
               .sum(sums[component])
               .sumOfSquares(sumsOfSquares[component])
               .build();
      }

      return ImageStats.create(index, componentStats);
   }

   private <T extends IntegerType<T>> IterableInterval<T> clipToRect(
         RandomAccessibleInterval<T> fullImg, int nComponents, Rectangle statsBounds) {
      Preconditions.checkNotNull(statsBounds);
      return Views.interval(fullImg,
            Intervals.createMinSize(
                  0, statsBounds.x, statsBounds.y,
                  nComponents, statsBounds.width, statsBounds.height)
      );
   }

   private IterableInterval<UnsignedByteType> wrapROIMask(
         byte[] rawMask, int nComponents, Rectangle maskBounds, Rectangle statsBounds) {
      Preconditions.checkNotNull(maskBounds);
      Preconditions.checkNotNull(statsBounds);
      if (rawMask == null) {
         return Views.iterable(
               ConstantUtils.constantRandomAccessibleInterval(
                     new UnsignedByteType(255),
                     3,
                     Intervals.createMinSize(0, statsBounds.x, statsBounds.y,
                           nComponents, statsBounds.width, statsBounds.height)));
      }

      // The 2D mask, positioned in the image's coordinate system
      IntervalView<UnsignedByteType> mask = Views.translate(
            ArrayImgs.unsignedBytes(rawMask,
                  maskBounds.width, maskBounds.height),
            maskBounds.x, maskBounds.y);

      // Add the component dimension and clip to the intersection with the image
      long[] min = {0, statsBounds.x, statsBounds.y};
      long[] max = {nComponents - 1, statsBounds.x + statsBounds.width - 1,
            statsBounds.y + statsBounds.height - 1};
      MixedTransform t = new MixedTransform(3, 2);
      t.setComponentMapping(new int[] {1, 2});
      return Views.iterable(Views.interval(
            new MixedTransformView<UnsignedByteType>(mask, t),
            min, max
      ));
   }
}