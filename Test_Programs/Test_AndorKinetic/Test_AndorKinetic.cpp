// Test_AndorKinetic.cpp : Defines the entry point for the console application.
//

#include "stdafx.h"
#include "../../../3rdparty/Andor/SDK 2.77/atmcd32d.h"


int acquire()
{
   const float exp_s = 0.001F;
   const float cycleTime_s = 0.500F;
   const int numFrames = 10;

   // ??? enabling frame transfer does not make any difference in timing ???
   //ret = SetFrameTransferMode(1);
   //if (ret != DRV_SUCCESS)
   //   return ret;

   // camera acquistion and read modes
   unsigned ret = SetAcquisitionMode(5); // run till abort
   if (ret != DRV_SUCCESS)
      return ret;

   ret = SetReadMode(4); // image mode
   if (ret != DRV_SUCCESS)
      return ret;

   ret = SetADChannel(0);
   if (ret != DRV_SUCCESS)
      return ret;

   // set image
   int xdim, ydim;
   GetDetector(&xdim, &ydim);
  	SetImage(4,4,1,xdim,1,ydim);

   // set exposure
   SetExposureTime(exp_s);

   // number of accumulations
   ret = SetNumberAccumulations(1);
   if (ret != DRV_SUCCESS)
      return ret;

   //ret = SetNumberKinetics(numFrames);
   //if (ret != DRV_SUCCESS)
   //{
   //   return ret;
   //}

   // set frame interval
   ret = SetKineticCycleTime(cycleTime_s);
   if (ret != DRV_SUCCESS)
      return ret;

   // report desired and actual settings
   float fExposure, fAccumTime, fKineticTime;
   GetAcquisitionTimings(&fExposure,&fAccumTime,&fKineticTime);
   printf("Acquisition seetings: exposure=%.3f s, kinetic cycle=%.3f s, frames=%d\n", exp_s, cycleTime_s, numFrames);
   printf("Actual timings: exposure=%.3f s, kinetic cycle=%.3f s, accum time=%.3f\n", fExposure, fKineticTime, fAccumTime);

   // START ACQUIRING
   // ===============

   long acc;
   long seriesInit;
   long series;
   DWORD startT = GetTickCount();
   ret = StartAcquisition();
   if (ret != DRV_SUCCESS)
   {
      return ret;
   }

   ret = GetAcquisitionProgress(&acc, &seriesInit);
   if (ret != DRV_SUCCESS)
   {
      return ret;
   }

   do
   {
      ret = GetAcquisitionProgress(&acc, &series);
      if (ret != DRV_SUCCESS)
      {
         return ret;
      }
   } while (series == seriesInit);

   long seriesPrev = 0;
   int status=0;
   int frameCounter=0;
   DWORD timePrev = GetTickCount();
   do
   {
      GetStatus(&status);
      ret = GetAcquisitionProgress(&acc, &series);
      printf("series = %ld at %ld ms!\n", series, GetTickCount() - timePrev);
      if (series > seriesPrev)
      {
         // new frame arrived
         // use get GetMostRecentImage16() to collect pixels

         // report time elapsed since previous frame
         printf("Frame %d captured at %ld ms!\n", ++frameCounter, GetTickCount() - timePrev);
         seriesPrev = series;
         timePrev = GetTickCount();
      }
      Sleep(100);
   }
   while (ret == DRV_SUCCESS && series - seriesInit < numFrames);

   DWORD deltaT = GetTickCount() - startT;
   printf("Kinetic finished, status = %d, total time = %ld ms, effective interval %ld\n", status, deltaT, deltaT/numFrames);

   if (ret != DRV_SUCCESS && series != 0)
   {
      AbortAcquisition();
      printf("Error %d\n", ret);
      return ret;
   }

   if (series == numFrames)
   {
      printf("Done!\n");
      AbortAcquisition(); // finished
   }

	return 0;
}

int _tmain(int argc, _TCHAR* argv[])
{
   //unsigned ret = ::Initialize("\\Program Files\\Andor iXon\\Drivers");
   unsigned ret = ::Initialize("\\Program Files\\Andor iXon\\Drivers\\Examples\\C\\Kinetic Image");
   if (ret != DRV_SUCCESS)
      return ret;
   ret = SetAcquisitionMode(1); // run till abort
   if (ret != DRV_SUCCESS)
      return ret;
   // Set Vertical speed to max
   float STemp = 0;
   int VSnumber = 0;
	int index;
	float speed;

   GetNumberVSSpeeds(&index);
   for(int i=0; i<index; i++)
   {
      GetVSSpeed(i, &speed);
      if(speed > STemp)
      {
         STemp = speed;
         VSnumber = i;
      }
   }

   ret = SetVSSpeed(VSnumber);
   if(ret != DRV_SUCCESS)
      return ret;

   // Set Horizontal Speed to max
   STemp = 0;
   int HSnumber = 0;
   GetNumberHSSpeeds(0,0,&index);
   for(int i=0; i<index; i++)
   {
      GetHSSpeed(0, 0, i, &speed);
      if(speed > STemp)
      {
        STemp = speed;
        HSnumber = i;
      }
   }

   ret = SetHSSpeed(0,HSnumber);
   if(ret != DRV_SUCCESS)
      return ret;

   // set shutter mode to open
   const int modeIdx = 1;
   ret = SetShutter(1, modeIdx, 0, 0);
   if (ret != DRV_SUCCESS)
      return (int)ret;

   // 1st acq
   ret = acquire();
   if (ret != 0)
      return ret;
   ret = SetAcquisitionMode(1); // run till abort
   if (ret != DRV_SUCCESS)
      return ret;

   // 2nd acq
   ret = acquire();
 
   ret = SetShutter(1, 0, 0, 0);
   if (ret != DRV_SUCCESS)
      return (int)ret;

   ShutDown();

   return 0;
}

