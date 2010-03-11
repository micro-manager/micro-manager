#include "MMAcquisition.h"



//////////////////////
// ImageGrabberTask //
//////////////////////

class ImageGrabberTask:public MMRunnable
{

private:
   CoreCallback* coreCallback_;

public:
   ImageGrabberTask(CoreCallback* coreCallback)
   {
      coreCallback_ = coreCallback;

   }

   void run() {
      const char * img = coreCallback_->GetImage();
      int w,h,d;
      coreCallback_->GetImageDimensions(w, h, d);
      coreCallback_->InsertImage(NULL, (const unsigned char *) img, w, h, d);
   }
};

/////////////////
// SleeperTask //
/////////////////

class SleeperTask:public MMRunnable
{

private:
   CoreCallback* coreCallback_;
   MM::MMTime interval_;
   static MM::MMTime lastWakeTime_;
   
public:
   SleeperTask(CoreCallback* coreCallback, double millisecondsToSleepAfterLastWake)
   {
      coreCallback_ = coreCallback;
      interval_ = 1000 * millisecondsToSleepAfterLastWake;
   }

   void run() {
      if (lastWakeTime_ > 0)
      {
         MM::MMTime sleepTime = SleeperTask::lastWakeTime_ + interval_ - coreCallback_->GetCurrentMMTime();
         coreCallback_->Sleep(NULL, sleepTime.getMsec());
      }

      SleeperTask::lastWakeTime_ = coreCallback_->GetCurrentMMTime();
   }

   static void reset() {
      SleeperTask::lastWakeTime_ = -1;
   }
};

MM::MMTime SleeperTask::lastWakeTime_;
//void SleeperTask::reset();


/////////////////////////
// MMAcquisitionRunner //
/////////////////////////


void MMAcquisitionRunner::Start()
{
   Run();
}

void MMAcquisitionRunner::Run()
{
   for (unsigned int i=0;i<tasks_.size();++i)
   {
      printf("Task %d started.\n", i);
      tasks_[i]->run();
   }
}

void MMAcquisitionRunner::Stop()
{
}

void MMAcquisitionRunner::Pause()
{
}

void MMAcquisitionRunner::Resume()
{
}

void MMAcquisitionRunner::Step()
{
}


void MMAcquisitionRunner::SetTasks(TaskVector tasks)
{
   tasks_ = tasks;
}


////////////////////////////
// MMAcquisitionSequencer //
////////////////////////////


MMAcquisitionSequencer::MMAcquisitionSequencer(CoreCallback * coreCallback)
{
   coreCallback_ = coreCallback;
}

TaskVector MMAcquisitionSequencer::generateTaskVector()
{
   TaskVector taskVector;
   ImageGrabberTask * imageGrabberTask = new ImageGrabberTask(coreCallback_);
   SleeperTask * sleeperTask = new SleeperTask(coreCallback_, 10000);
   SleeperTask::reset();

   for (int i=0;i<5;++i) {
      taskVector.push_back(sleeperTask);
      taskVector.push_back(imageGrabberTask);
   }

   return taskVector;
}


TaskVector MMAcquisitionSequencer::generateSlicesAndChannelsLoop()
{
   TaskVector taskVector;
   return taskVector;
}

void MMAcquisitionSequencer::setAcquisitionSettings(AcquisitionSettings acquisitionSettings)
{
   acquisitionSettings_ = acquisitionSettings;
}

/////////////////////////
// MMAcquisitionEngine //
/////////////////////////

void MMAcquisitionEngine::runTest()
{
   core_->logMessage("running acquisition engine test...");
   MMAcquisitionSequencer * sequencer_ = new MMAcquisitionSequencer(coreCallback_);
   TaskVector taskVector = sequencer_->generateTaskVector();

   runner_ = new MMAcquisitionRunner();
   runner_->SetTasks(taskVector);
   runner_->Start();
}