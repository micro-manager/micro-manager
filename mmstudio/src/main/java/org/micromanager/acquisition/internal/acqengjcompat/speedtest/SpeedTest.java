package org.micromanager.acquisition.internal.acqengjcompat.speedtest;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.Function;
import mmcorej.CMMCore;
import org.micromanager.acqj.main.Acquisition;
import org.micromanager.acqj.main.AcquisitionEvent;
import org.micromanager.acqj.util.AcqEventModules;
import org.micromanager.acqj.util.AcquisitionEventIterator;
import org.micromanager.ndtiffstorage.ImageWrittenListener;
import org.micromanager.ndtiffstorage.IndexEntryData;

public class SpeedTest  {

   private ArrayList<int[]> queueData = new ArrayList<>();
   private ArrayList<long[]> imageData = new ArrayList<>();
   private long startTime = System.currentTimeMillis();

   public static void runSpeedTest(String dir, String name,
                                   CMMCore core_, int numTimePoints, boolean showViewer ) {
      try {
         if (core_.hasProperty("Camera", "FastImage")) {
            // demo camera
            core_.setProperty("Camera", "FastImage", false);
            core_.snapImage();
            core_.setProperty("Camera", "FastImage", true);


            // so that the capicty is reported correctly
            core_.clearCircularBuffer();
            core_.initializeCircularBuffer();
            core_.startContinuousSequenceAcquisition(0);
            core_.stopSequenceAcquisition();
         }

         NDTiffAndViewerAdapter ndTiffAndViewerAdapter = new NDTiffAndViewerAdapter(showViewer,
               dir, name, 10);


         Acquisition acquisition = new Acquisition(ndTiffAndViewerAdapter);


         SpeedTest speedTest = new SpeedTest(core_.getBufferTotalCapacity(),
               acquisition.getImageTransferQueueSize(),
               ndTiffAndViewerAdapter.getStorage().getWritingQueueTaskMaxSize());

         ndTiffAndViewerAdapter.getStorage().addImageWrittenListener(new ImageWrittenListener() {
            int imageCount = 0;

            @Override
            public void imageWritten(IndexEntryData ied) {
               imageCount++;
               speedTest.imageWritten(imageCount);
            }

            @Override
            public void awaitCompletion() {

            }
         });


         acquisition.start();
         long start = System.currentTimeMillis();

         acquisition.submitEventIterator(createSpeedTestEvents(acquisition, numTimePoints));
         acquisition.finish();

         while (!acquisition.areEventsFinished()) {
            int bufferFreeCapacity = core_.getBufferTotalCapacity() - core_.getBufferFreeCapacity();
            int acqEngQueueCount = acquisition.getImageTransferQueueCount();
            int writingTaskCount = ndTiffAndViewerAdapter.getStorage().getWritingQueueTaskSize();
            speedTest.logStatus(System.currentTimeMillis(), bufferFreeCapacity,
                  acqEngQueueCount, writingTaskCount);
            try {
               Thread.sleep(3);
            } catch (InterruptedException e) {
               throw new RuntimeException(e);
            }
         }

         acquisition.waitForCompletion();

         System.out
               .println("Speed test complete in " + (System.currentTimeMillis() - start) + " ms");

         speedTest.save(ndTiffAndViewerAdapter.getDiskLocation() + "/"
                     + ndTiffAndViewerAdapter.getStorage().getUniqueAcqName() + "_queues.csv",
               ndTiffAndViewerAdapter.getDiskLocation() + "/"
                     + ndTiffAndViewerAdapter.getStorage().getUniqueAcqName() + "_image_data.csv",
               (int) (core_.getImageHeight() * core_.getImageWidth() * core_.getBytesPerPixel())
         );
         acquisition.checkForExceptions();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   private static Iterator<AcquisitionEvent> createSpeedTestEvents(Acquisition a, int numTimePoints) {
      AcquisitionEvent baseEvent = new AcquisitionEvent(a);
      ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> acqFunctions
            = new ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>>();
      acqFunctions.add(AcqEventModules.timelapse(numTimePoints, 0.0));
      return new AcquisitionEventIterator(baseEvent, acqFunctions);
   }

   private SpeedTest(int circBufferSizeMax, int acqEngOutputQueueSize, int writingTaskQueueSizeMax) {
      queueData.add(new int[]{circBufferSizeMax, acqEngOutputQueueSize, writingTaskQueueSizeMax});
   }

    private void logStatus(long time, int circularBuffer, int acqEngOutput, int writingTask){
       queueData.add(new int[]{(int) (time - startTime), circularBuffer, acqEngOutput, writingTask});
    }

    private void save(String queuePath, String dataPath, int bytesPerImage) throws IOException {
       File csvFile = new File(queuePath);
       FileWriter fileWriter = new FileWriter(csvFile);

       //write header line here if you need.

       int[] max = queueData.remove(0);
       fileWriter.write("," + max[0] + "," + max[1] + "," + max[2] + "\n");

       for (int[] line : queueData) {
          fileWriter.write(line[0] + "," + line[1] + "," + line[2] + "," + line[3] + "\n");
       }
       fileWriter.close();


       csvFile = new File(dataPath);
       fileWriter = new FileWriter(csvFile);

       for (long[] line : imageData) {
          fileWriter.write(line[0] + "," + (bytesPerImage * line[1]) + "\n");
       }
       fileWriter.close();

    }


   private void imageWritten(int count) {
      imageData.add(new long[]{System.currentTimeMillis(), count});
   }
}