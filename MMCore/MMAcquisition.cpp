#include "MMAcquisition.h"

////////////////////////
// MMAcquisitionState //
////////////////////////

class MMAcquisitionState {
public:
   CMMCore * core;
   CoreCallback * coreCallback;

   int frameCount;
   double zReference;
   MM::MMTime lastWakeTime;

   MMAcquisitionState(CMMCore * _core, CoreCallback * _coreCallback)
   {
         core = _core;
         coreCallback = _coreCallback;
         frameCount = 0;
         lastWakeTime = -1;

   }
};


//////////////
// TimeTask //
//////////////

class TimeTask:public MMRunnable
{

private:
   MMAcquisitionState * state_;
   MM::MMTime interval_;
   
public:
   TimeTask(MMAcquisitionState * state, double millisecondsToSleepAfterLastWake)
   {
      type = TIME;
      state_ = state;
      interval_ = 1000 * millisecondsToSleepAfterLastWake;
   }

   void run() {
      if (state_->lastWakeTime > 0)
      {
         MM::MMTime sleepTime = state_->lastWakeTime + MM::MMTime(interval_) - state_->coreCallback->GetCurrentMMTime();
         if (sleepTime > MM::MMTime(0,0))
            state_->coreCallback->Sleep(NULL, sleepTime.getMsec());
         ++(state_->frameCount);
         printf("sleep\n");
      }

      state_->lastWakeTime = state_->coreCallback->GetCurrentMMTime();

   }

};



//////////////////
// PositionTask //
//////////////////


class PositionTask:public MMRunnable
{
private:
   MMAcquisitionState * state_;
   MultiAxisPosition * pos_;

public:
   PositionTask(MMAcquisitionState * state, MultiAxisPosition * pos)
   {
      type = POSITION;
      state_ = state;
      pos_ = pos;
   }

   void run() {
      std::map<std::string,double>::iterator it1;

      for(it1 = pos_->singleAxisPositions.begin(); it1 != pos_->singleAxisPositions.end(); ++it1)
      {
         state_->core->setPosition(it1->first.c_str(), it1->second);
      } 

      std::map<std::string,pair<double,double>>::iterator it2;

      for(it2 = pos_->doubleAxisPositions.begin(); it2 != pos_->doubleAxisPositions.end(); ++it2)
      {
         pair<double,double> xy = it2->second;
         state_->core->setXYPosition(it2->first.c_str(),xy.first,xy.second);
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
   MMAcquisitionState * state_;
   double pos_;

public:
   SliceTask(MMAcquisitionState * state, double pos)
   {
      type = SLICE;
      state_ = state;
      pos_ = pos;

   }

   void run() {
      state_->coreCallback->SetFocusPosition(pos_);
      printf("set slice\n");
   }
};

/////////////////
// ChannelTask //
/////////////////

class ChannelTask:public MMRunnable
{
private:
   MMAcquisitionState* state_;
   Channel * channel_;

public:
   ChannelTask(MMAcquisitionState * state, Channel * channel)
   {
      type = CHANNEL;
      state_ = state;
      channel_ = channel;
   }

   void run() {
      if (0==(state_->frameCount % (1 + channel_->skipFrames)))
      {
         state_->coreCallback->SetExposure(channel_->exposure);
         state_->coreCallback->SetConfig(channel_->group.c_str(), channel_->name.c_str());
         printf("set channel\n");
      }
   }
};


/////////////////
// AutofocusTask //
/////////////////

class AutofocusTask:public MMRunnable
{
private:
   MMAcquisitionState* state_;
   int skipFrames_;

public:
   AutofocusTask(MMAcquisitionState * state, int skipFrames)
   {
      type = AUTOFOCUS;
      state_ = state;
      skipFrames_ = skipFrames;
   }

   void run() {
      if (0==(state_->frameCount % (1 + skipFrames_)))
      {
         state_->core->fullFocus();
         printf("fullFocus()");
      }
   }
};


///////////////
// ImageTask //
///////////////

class ImageTask:public MMRunnable
{

private:
   MMAcquisitionState * state_;

public:
   ImageTask(MMAcquisitionState * state)
   {
      state_ = state;
      type = IMAGE;
   }

   void run() {

      const char * img = state_->coreCallback->GetImage();
      int w,h,d;
      state_->coreCallback->GetImageDimensions(w, h, d);
      state_->coreCallback->InsertImage(NULL, (const unsigned char *) img, w, h, d);
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
      printf("Task #%d started, type %d\n", i, tasks_[i]->type);
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

   MMAcquisitionState * state = new MMAcquisitionState(core_, coreCallback_);


   ImageTask * imageTask = new ImageTask(state);

   TaskVector channelVector;
   Channel * channel1 = new Channel("Channel","DAPI",100);
   Channel * channel2 = new Channel("Channel","FITC",150);
   channelVector.push_back(new ChannelTask(state, channel1)); 
   channelVector.push_back(new ChannelTask(state, channel2));

   TaskVector sliceVector;
   sliceVector.push_back(new SliceTask(state, 0.));
   sliceVector.push_back(new SliceTask(state, 1.));

   TaskVector positionVector;
   MultiAxisPosition * pos1 = new MultiAxisPosition();
   pos1->AddDoubleAxisPosition("XY",1,3);
   PositionTask* posTask1 = new PositionTask(state, pos1);
   positionVector.push_back(posTask1);

   MultiAxisPosition * pos2 = new MultiAxisPosition();
   pos2->AddDoubleAxisPosition("XY",4,5);
   pos2->AddOneSingleAxisPosition("Z",1);
   PositionTask* posTask2 = new PositionTask(state, pos2);
   positionVector.push_back(posTask2);

   TimeTask * timeTask = new TimeTask(state, 1000);

   TaskVector timeVector(10, timeTask);

   return generateMDASequence(imageTask, timeVector, positionVector, channelVector, sliceVector);
}

TaskVector MMAcquisitionSequencer::generateMDASequence(MMRunnable * imageTask,
   TaskVector timeVector, TaskVector positionVector, TaskVector channelVector, TaskVector sliceVector)
{  
   TaskVector allTasks;

   allTasks.push_back(imageTask);

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
      allTasks = NestTasks(positionVector, allTasks);
      allTasks = NestTasks(timeVector, allTasks);
   } else {
      allTasks = NestTasks(timeVector, allTasks);
      allTasks = NestTasks(positionVector, allTasks);
   }
   
   printf("allTasks.size() = %d",allTasks.size());
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