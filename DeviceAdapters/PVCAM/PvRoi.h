#ifndef _PVROI_H_
#define _PVROI_H_

#include "PVCAMIncludes.h"

/***
* User selected region of interest
*/
struct PvRoi {
   uns16 x;
   uns16 newX;
   uns16 y;
   uns16 newY;
   uns16 xSize;
   uns16 newXSize;
   uns16 ySize;
   uns16 newYSize;
   uns16 binXSize;
   uns16 binYSize;

   // added this function to the ROI struct because it only applies to this data structure,
   //  and nothing else.
   void PVCAMRegion(uns16 x_, uns16 y_, uns16 xSize_, uns16 ySize_, \
                    unsigned binXSize_, unsigned binYSize_, rgn_type &newRegion)
   {
      // set to full frame
      x = x_;
      y = y_;
      xSize = xSize_;
      ySize = ySize_;

      // set our member binning information
      binXSize = (uns16) binXSize_;
      binYSize = (uns16) binYSize_;

      // save ROI-related dimentions into other data members
      newX = x/binXSize;
      newY = y/binYSize;
      newXSize = xSize/binXSize;
      newYSize = ySize/binYSize;

      // round the sizes to the proper devisible boundaries
      x = newX * binXSize;
      y = newY * binYSize;
      xSize = newXSize * binXSize;
      ySize = newYSize * binYSize;

      // set PVCAM-specific region
      newRegion.s1 = x;
      newRegion.s2 = x + xSize-1;
      newRegion.sbin = binXSize;
      newRegion.p1 = y;
      newRegion.p2 = y + ySize-1;
      newRegion.pbin = binYSize;
   }
};

#endif // _PVROI_H_