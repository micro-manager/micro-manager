
#ifndef MMACQUISITION_H
#define MMACQUISITION_H

#include "CoreCallback.h"

#include "MMRunnable.h"
#include "../MMDevice/DeviceBase.h"


typedef map<string, string> PropertyMap;

//////////////////
// ImageRequest //
//////////////////

class ImageRequest {
public:
   MM::MMTime waitTime;
   double exposure;
   MultiAxisPosition multiAxisPosition;
   Channel channel;
   double slicePosition;
   bool runAutofocus;

   int sourceIndex;
   int positionIndex;
   int timeIndex;
   int sliceIndex;
   int channelIndex;

   bool usePosition;
   bool useTime;
   bool useSlice;
   bool useChannel;

   bool closeShutter;

   ImageRequest():
   sourceIndex(0),
      positionIndex(0),
      timeIndex(0),
      sliceIndex(0),
      channelIndex(0) {

   }
};



/////////////////////////
// MMAcquisitionEngine //
/////////////////////////

class MMAcquisitionEngine:MMDeviceThreadBase
{
private:
   TaskVector tasks_;

   bool pauseRequested_;
   bool stopRequested_;
   bool finished_;

   int svc() { Run(); return 0; }
   map<string, string> initialPropertyMap_;
   
public:
   MM::MMTime lastWakeTime_;
   CMMCore * core_;
   CoreCallback * coreCallback_;

   MMAcquisitionEngine(CMMCore * core)
   {
      core_ = core;
      coreCallback_ = new CoreCallback(core);
   }

   void Prepare(AcquisitionSettings acquisitionSettings);
   void Run();
   void Start();
   void Stop();
   void Pause();
   void Resume();
   void Step();
   bool StopHasBeenRequested();
   bool IsFinished();

   void SetTasks(TaskVector tasks);

   map<string, string> GetCurrentPropertyMap();
   void ApplyDiffPropertyMap(map<string, string> & dest);
   map<string, string> GetInitPropertyMap();

   void GenerateSequence(AcquisitionSettings acquisitionSettings);
   void GenerateSequence2(AcquisitionSettings acquisitionSettings);
   void GenerateSlicesAndChannelsSubsequence(AcquisitionSettings acquisitionSettings, ImageRequest request);
   void ControlShutterStates(AcquisitionSettings acquisitionSettings);
   vector<int> GenerateIndices(int n);
   //MMRunnable * createImageTask();


};


class ImageTask:public MMRunnable
{

private:
   MMAcquisitionEngine * eng_;

   void updatePosition();
   void updateSlice();
   void updateChannel();
   void wait();
   void autofocus();
   void acquireImage();

public:
   ImageRequest imageRequest_;
   ImageTask(MMAcquisitionEngine * eng, ImageRequest imageRequest);
   void run();
};

#endif
