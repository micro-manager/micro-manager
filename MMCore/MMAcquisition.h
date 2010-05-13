

#include "CoreCallback.h"

#include "MMRunnable.h"
#include "../MMDevice/DeviceBase.h"



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

public:
   MM::MMTime lastWakeTime_;
   CMMCore * core_;
   CoreCallback * coreCallback_;

   MMAcquisitionEngine(CMMCore * core)
   {
      core_ = core;
      coreCallback_ = new CoreCallback(core);
   }

   void Run();
   void Start();
   void Stop();
   void Pause();
   void Resume();
   void Step();
   bool IsFinished();

   void SetTasks(TaskVector tasks);

   void GenerateSequence(AcquisitionSettings acquisitionSettings);
   void GenerateSlicesAndChannelsSubsequence(AcquisitionSettings acquisitionSettings, ImageRequest request);
   //MMRunnable * createImageTask();


};


class ImageTask:public MMRunnable
{

private:
   MMAcquisitionEngine * eng_;
   ImageRequest imageRequest_;

   void updatePosition();
   void updateSlice();
   void updateChannel();
   void wait();
   void autofocus();
   void acquireImage();

public:
   ImageTask(MMAcquisitionEngine * eng, ImageRequest imageRequest);
   void run();

};