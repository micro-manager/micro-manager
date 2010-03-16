#include "MMAcquisition.h"



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
      printf("sleep\n");
   }

   static void reset() {
      SleeperTask::lastWakeTime_ = -1;
   }
};

MM::MMTime SleeperTask::lastWakeTime_;
//void SleeperTask::reset();



///////////////////////
// MultiPositionTask //
///////////////////////


class MultiPositionTask:public MMRunnable
{
private:
   CMMCore* core_;
   std::map<std::string,double> singleAxisPositions_;
   std::map<std::string,pair<double,double>> doubleAxisPositions_;

public:
   MultiPositionTask(CMMCore* core)
   {
      core_ = core;
   }

   void AddOneSingleAxisPosition(string name, double pos)
   {
      singleAxisPositions_[name] = pos;
   }

   void AddDoubleAxisPosition(string name, double posX, double posY)
   {
      doubleAxisPositions_[name] = pair<double,double>(posX,posY);
   }

   void run() {
      std::map<std::string,double>::iterator it1;

      for(it1 = singleAxisPositions_.begin(); it1 != singleAxisPositions_.end(); ++it1)
      {
         core_->setPosition(it1->first.c_str(), it1->second);
      } 

      std::map<std::string,pair<double,double>>::iterator it2;

      for(it2 = doubleAxisPositions_.begin(); it2 != doubleAxisPositions_.end(); ++it2)
      {
         pair<double,double> xy = it2->second;
         core_->setXYPosition(it2->first.c_str(),xy.first,xy.second);
      } 
      printf("set position\n");
   }

};




///////////////
// SliceTask //
///////////////

class SliceTask:public MMRunnable
{
private:
   CoreCallback* coreCallback_;
   double pos_;

public:
   SliceTask(CoreCallback* coreCallback, double pos)
   {
      coreCallback_ = coreCallback;
      pos_ = pos;

   }

   void run() {
      coreCallback_->SetFocusPosition(pos_);
      printf("set slice\n");
   }
};

/////////////////
// ChannelTask //
/////////////////

class ChannelTask:public MMRunnable
{
private:
   CoreCallback* coreCallback_;
   string channelGroup_;
   string channelName_;

public:
   ChannelTask(CoreCallback* coreCallback, std::string channelGroup, std::string channelName)
   {
      coreCallback_ = coreCallback;
      channelGroup_ = channelGroup;
      channelName_ = channelName;
   }

   void run() {
      coreCallback_->SetConfig(channelGroup_.c_str(), channelName_.c_str());
      printf("set channel\n");
   }
};


/////////////////
// AutofocusTask //
/////////////////

class AutofocusTask:public MMRunnable
{
private:
   CMMCore* core_;

public:
   AutofocusTask(CMMCore* core)
   {
      core_ = core;
   }

   void run() {
      core_->fullFocus();
      printf("fullFocus()");
   }
};


//////////////////////
// ImageGrabberTask //
//////////////////////

class ImageGrabberTask:public MMRunnable
{

private:
   CMMCore * core_;
   CoreCallback* coreCallback_;

public:
   ImageGrabberTask(CMMCore * core, CoreCallback* coreCallback)
   {
      core_ = core;
      coreCallback_ = coreCallback;
   }

   void run() {
      const char * img = coreCallback_->GetImage();
      int w,h,d;
      coreCallback_->GetImageDimensions(w, h, d);
      coreCallback_->InsertImage(NULL, (const unsigned char *) img, w, h, d);
      printf("Grabbed image.\n");
   }
};


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


MMAcquisitionSequencer::MMAcquisitionSequencer(CMMCore * core, CoreCallback * coreCallback, AcquisitionSettings acquisitionSettings)
{
   core_ = core;
   coreCallback_ = coreCallback;
   acquisitionSettings_ = acquisitionSettings;
}

TaskVector MMAcquisitionSequencer::generateTaskVector()
{
   //TaskVector snapVector;
   //TaskVector channelVector;
   //TaskVector sliceVector;
   //TaskVector positionVector;
   //TaskVector timeVector;
   TaskVector allTasks;

   ImageGrabberTask * imageGrabberTask = new ImageGrabberTask(core_, coreCallback_);
   SleeperTask * sleeperTask = new SleeperTask(coreCallback_, 1000);
   SleeperTask::reset();

   allTasks.push_back(imageGrabberTask);

   TaskVector channelVector;
   channelVector.push_back(new ChannelTask(coreCallback_, "Channel","DAPI"));
   channelVector.push_back(new ChannelTask(coreCallback_, "Channel","FITC"));

   TaskVector sliceVector;
   sliceVector.push_back(new SliceTask(coreCallback_, 0.));
   sliceVector.push_back(new SliceTask(coreCallback_, 1.));

   TaskVector multiPositionVector;
   MultiPositionTask* posTask1 = new MultiPositionTask(core_);
   posTask1->AddDoubleAxisPosition("XY",1,3);
   multiPositionVector.push_back(posTask1);
   MultiPositionTask* posTask2 = new MultiPositionTask(core_);
   posTask2->AddDoubleAxisPosition("XY",4,5);
   posTask2->AddOneSingleAxisPosition("Z",1);
   multiPositionVector.push_back(posTask2);

   TaskVector timeVector(10, sleeperTask);

   if (acquisitionSettings_.channelsFirst)
   {
      allTasks = NestTasks(sliceVector, allTasks);
      allTasks = NestTasks(channelVector, allTasks);
   } else {
      allTasks = NestTasks(channelVector, allTasks);
      allTasks = NestTasks(sliceVector, allTasks);
   }

   if (acquisitionSettings_.positionsFirst)
   {
      allTasks = NestTasks(multiPositionVector, allTasks);
      allTasks = NestTasks(timeVector, allTasks);
   } else {
      allTasks = NestTasks(timeVector, allTasks);
      allTasks = NestTasks(multiPositionVector, allTasks);
   }

   return allTasks;
}

TaskVector MMAcquisitionSequencer::NestTasks(TaskVector outerTasks, TaskVector innerTasks)
{
   TaskVector nestedTasks;

   for(TaskVector::iterator it = outerTasks.begin(); it < outerTasks.end(); ++it)
   {
      // Append next element of outerTasks.
      nestedTasks.push_back(*it);

      // Append all elements of innerTasks.
      nestedTasks.insert(nestedTasks.end(),innerTasks.begin(),innerTasks.end());
   }

   return nestedTasks;
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
   AcquisitionSettings acquisitionSettings;
   acquisitionSettings.positionsFirst = true;
   acquisitionSettings.channelsFirst = true;

   core_->logMessage("running acquisition engine test...");
   sequencer_ = new MMAcquisitionSequencer(core_, coreCallback_, acquisitionSettings);
   TaskVector taskVector = sequencer_->generateTaskVector();
   delete sequencer_;

   runner_ = new MMAcquisitionRunner();
   runner_->SetTasks(taskVector);
   runner_->Start();
   delete runner_;
}