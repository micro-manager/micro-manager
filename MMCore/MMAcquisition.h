

#include "CoreCallback.h"

#include "MMRunnable.h"
//#include "DeviceThreads.h"





class MMAcquisitionRunner:MMDeviceThreadBase
{
private:
   TaskVector tasks_;
   bool pauseRequested_;
   bool stopRequested_;
   bool finished_;

   int svc() { Run(); return 0; }

public:
   ~MMAcquisitionRunner() {}

   void Run();
   void Start();
   void Stop();
   void Pause();
   void Resume();
   void Step();
   bool IsFinished();

   void SetTasks(TaskVector tasks);
};


class MMAcquisitionSequencer
{
private:
   CMMCore * core_;
   CoreCallback * coreCallback_;
   AcquisitionSettings acquisitionSettings_;
   TaskVector NestTasks(TaskVector outerTasks, TaskVector innerTasks);

public:
   MMAcquisitionSequencer(CMMCore * core, CoreCallback * coreCallback, AcquisitionSettings acquisitionSettings);
   TaskVector generateTaskVector();
   TaskVector generateMDASequence(MMRunnable * imageTask,
      TaskVector timeVector, TaskVector positionVector,
      TaskVector channelVector, TaskVector sliceVector);
   void setAcquisitionSettings(AcquisitionSettings settings);

};

class MMAcquisitionEngine
{
private:
   CMMCore * core_;
   CoreCallback * coreCallback_;
   MMAcquisitionRunner runner_;

public:
   MMAcquisitionEngine(CMMCore * core) { 
      core_ = core;
      coreCallback_ = new CoreCallback(core);
   }
   void runTest(AcquisitionSettings acquisitionSettings);
   bool isFinished();
};