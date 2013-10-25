package HDF;

import java.util.LinkedList;


/**
 *
 * @author Henry
 */
public class ResolutionLevelMaker {
   
   private static final int BYTES_PER_MB = 1024*1024;   
   
   public static ResolutionLevel[] calcLevels(int imageSizeX, int imageSizeY, int imageSizeZ, int numTimePoints, int byteDepth) {   
      LinkedList<ResolutionLevel> resLevels = new LinkedList<ResolutionLevel>();
      addResLevels(resLevels, imageSizeX, imageSizeY, imageSizeZ, 
              imageSizeX, imageSizeY, imageSizeZ, numTimePoints,byteDepth);
      ResolutionLevel[] array = new ResolutionLevel[resLevels.size()];
      for (int i = 0; i < array.length; i++) {
         array[i] = resLevels.get(i);
      }
      return array;
   }
   
   //Make res level 0 and add more if needed
   private static void addResLevels(LinkedList<ResolutionLevel> resLevels, int baseSizeX, int baseSizeY, int baseSizeZ,
           int imageSizeX, int imageSizeY, int imageSizeZ, int numTimePoints, int byteDepth) {
      
      resLevels.add(new ResolutionLevel(resLevels.size(),baseSizeX,baseSizeY,baseSizeZ,
              imageSizeX,imageSizeY,imageSizeZ,numTimePoints,byteDepth) );
     
      if (resLevels.getLast().getImageNumBytes() > 4*BYTES_PER_MB) {
         int newX = imageSizeX, newY = imageSizeY, newZ = imageSizeZ;
         boolean reduceZ = (10 * imageSizeZ) * (10 * imageSizeZ) > imageSizeX * imageSizeY;
         boolean reduceY = (10 * imageSizeY) * (10 * imageSizeY) > imageSizeX * imageSizeZ;
         boolean reduceX = (10 * imageSizeX) * (10 * imageSizeX) > imageSizeY * imageSizeZ;
         if (reduceZ) {
            newZ = (int) Math.ceil(imageSizeZ / 2.0);
         }
         if (reduceX) {
            newX = (int) Math.ceil(imageSizeX / 2.0);
         }
         if (reduceY) {
            newY = (int) Math.ceil(imageSizeY / 2.0);
         }      
         addResLevels(resLevels, baseSizeX, baseSizeY, baseSizeZ, newX, newY, newZ,numTimePoints,byteDepth);
      }
   }

   
}
