/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package misc;

import org.micromanager.MMStudio;

/**
 * Means for plugin classes to access the core, to support tricky things
 */
public class CoreCommunicator {

   private static final int MAX_FRAME_OFFSET = 128;
   
   public static int getImageWidth() {
      return (int) MMStudio.getInstance().getCore().getImageWidth();
   }

   public static int getImageHeight() {
      return (int) MMStudio.getInstance().getCore().getImageHeight();
   }

   public static void main(String[] args) {
//      int[] lut = getCosineWarpLUT(1400);
      
      int[] src = new int[]{1, 2, 3, 4, 5};
      
      System.arraycopy(src, 0, dst, 0, MAX_FRAME_OFFSET);
      
      System.out.println();
   }

   //TODO:
   //0) change camera files
   //1) Do deinterlacing in Java
   //2) add rank filtering
   //3) try summing edge pixels to alleviate flat fielding (compare # pixels tosses to linescan from flat field slide)
   //   -but you would hve to subtract the offset?
   //4) java layer image accumulater
   //5) put images into circular buffer when in a certain mode
   //6) why 1400 pix per line?
   
   
   private static int getChannelOffset(int index) {
      return 0;
   }
   
   /**
    * Mirror every second line of the buffer so it can later be deinterlaced
    * @param buffer 
    */
   public static void mirrorBuffer(byte[] buffer, int rawWidth, int rawHeight, int channelIndex) {
      int channelPtr = MAX_FRAME_OFFSET + getChannelOffset(channelIndex);

      for (int y=0; y<rawHeight; y++) {
         // second half of the line reversed
         unsigned char* srcLinePtr = channelPtr + j*rawWidth + rawWidth/2;
         for (unsigned k=0; k<rawWidth/4; k++)
         {
            unsigned char temp;
            int mirrorIdx = rawWidth2 - k - 1;
            temp = srcLinePtr[k];
            srcLinePtr[k] = srcLinePtr[mirrorIdx];
            srcLinePtr[mirrorIdx] = temp;
         }
		 //warp offset is 0 or 1, additional channle offsets apply to main offset
		 int channelWarpOffset = channelOffsets_[i] % 2;
		 // shift the line using warp offset
		 memcpy(lineBuf_, srcLinePtr - channelWarpOffset, rawWidth2-channelWarpOffset);
		 memcpy(srcLinePtr, lineBuf_, rawWidth2);
	  }
      
      
   }
   

   
}
