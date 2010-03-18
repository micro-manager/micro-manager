#include "MMAcquisition.h"
#include "../MMDevice/ImageMetadata.h"

////////////////////////
// MMAcquisitionState //
////////////////////////

class MMAcquisitionState {
public:
   CMMCore * core;
   CoreCallback * coreCallback;
   AcquisitionSettings acquisitionSettings;

   int frameCount;
   double zReference;
   MM::MMTime lastWakeTime;

   Channel currentChannel;
   double currentSlice;
   MultiAxisPosition currentPosition;

   Metadata metadata;

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
   int frameIndex_;
   
public:
   TimeTask(MMAcquisitionState * state, int frameIndex)
   {
      type = TIME;
      state_ = state;
      frameIndex_ = frameIndex;
      interval_ = MM::MMTime(1000*state_->acquisitionSettings.timeSeries[frameIndex]);
   }

   void run() {
      if (state_->lastWakeTime > 0)
      {
         MM::MMTime sleepTime = (state_->lastWakeTime + interval_) - state_->coreCallback->GetCurrentMMTime();
         if (sleepTime > MM::MMTime(0,0))
            state_->coreCallback->Sleep(NULL, sleepTime.getMsec());
         ++(state_->frameCount);
         printf("sleep\n");
      }

      state_->lastWakeTime = state_->coreCallback->GetCurrentMMTime();
      state_->metadata.frameIndex = frameIndex_;
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
   int positionIndex_;

public:
   PositionTask(MMAcquisitionState * state, int positionIndex)
   {
      type = POSITION;
      state_ = state;
      pos_ = &(state_->acquisitionSettings.positionList[positionIndex]);
      positionIndex_ = positionIndex;
   }

   void run() {
      map<string,double>::iterator it1;

      for(it1 = pos_->singleAxisPositions.begin(); it1 != pos_->singleAxisPositions.end(); ++it1)
      {
         state_->core->setPosition(it1->first.c_str(), it1->second);
      } 

      map<string,pair<double,double>>::iterator it2;

      for(it2 = pos_->doubleAxisPositions.begin(); it2 != pos_->doubleAxisPositions.end(); ++it2)
      {
         point2D xy = it2->second;
         state_->core->setXYPosition(it2->first.c_str(),xy.first,xy.second);
      } 
      printf("set position\n");

      state_->currentPosition = *pos_;
      state_->metadata.positionIndex = positionIndex_;
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
   int sliceIndex_;

public:
   SliceTask(MMAcquisitionState * state, int sliceIndex)
   {
      type = SLICE;
      state_ = state;
      pos_ = state_->acquisitionSettings.zStack[sliceIndex];
      sliceIndex_ = sliceIndex;
   }

   void run() {
      state_->coreCallback->SetFocusPosition(pos_);
      printf("set slice\n");
      state_->currentSlice = pos_;
      state_->metadata.sliceIndex = sliceIndex_;
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
   int channelIndex_;
public:
   ChannelTask(MMAcquisitionState * state, int channelIndex)
   {
      type = CHANNEL;
      state_ = state;
      channel_ = &(state_->acquisitionSettings.channelList[channelIndex]);
      channelIndex_ = channelIndex;
   }

   void run() {
      if (0==(state_->frameCount % (1 + channel_->skipFrames)))
      {
         state_->coreCallback->SetExposure(channel_->exposure);
         state_->coreCallback->SetConfig(channel_->group.c_str(), channel_->name.c_str());
         printf("set channel\n");
         state_->currentChannel = * channel_;
         state_->metadata.channelIndex = channelIndex_;
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
      state_->coreCallback->InsertImage(NULL, (const unsigned char *) img, w, h, d, &(state_->metadata));
      printf("Grabbed image.\n");
   }
};




/////////////////////////
// MMAcquisitionRunner //
/////////////////////////


void MMAcquisitionRunner::Start()
{
   stopRequested_ = false;
   pauseRequested_ = false;
   finished_ = false;

   activate();
}

void MMAcquisitionRunner::Run()
{
   for (unsigned int i=0;i<tasks_.size();++i)
   {
      if (stopRequested_)
         break;
      printf("Task #%d started, type %d\n", i, tasks_[i]->type);
      tasks_[i]->run();
   }
   finished_ = true;
}

bool MMAcquisitionRunner::IsFinished()
{
   return finished_;
}

void MMAcquisitionRunner::Stop()
{
   stopRequested_ = true;
}

void MMAcquisitionRunner::Pause()
{
   pauseRequested_ = true;
}

void MMAcquisitionRunner::Resume()
{
   pauseRequested_ = false;
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
/*

   AcquisitionSettings acquisitionSettings;


   
   Channel channel1("Channel","DAPI",100);
   Channel channel2("Channel","FITC",200);
   Channel channel3("Channel","Cy3",150);

   acquisitionSettings.channelList.push_back(channel1); 
   acquisitionSettings.channelList.push_back(channel2); 

   acquisitionSettings.zStack.push_back(0.);
   acquisitionSettings.zStack.push_back(1.);
   acquisitionSettings.zStack.push_back(2.);

   MultiAxisPosition pos1;
   pos1.AddDoubleAxisPosition("XY",1,3);
   MultiAxisPosition pos2;
   pos2.AddDoubleAxisPosition("XY",4,5);
   pos2.AddOneSingleAxisPosition("Z",1);
   acquisitionSettings.positionList.push_back(pos1);
   acquisitionSettings.positionList.push_back(pos2);

   for(unsigned i=0;i<5;++i)
      acquisitionSettings.timeSeries.push_back(6000.);
*/

   // Constructing the TaskVectors:

   MMAcquisitionState * state = new MMAcquisitionState(core_, coreCallback_);
   ImageTask * imageTask = new ImageTask(state);

   state->acquisitionSettings = acquisitionSettings_;

   TaskVector channelVector;
   for(unsigned i=0;i<acquisitionSettings_.channelList.size();++i)
      channelVector.push_back(new ChannelTask(state, i));

   TaskVector sliceVector;
   for(unsigned i=0;i<acquisitionSettings_.zStack.size();++i)
      sliceVector.push_back(new SliceTask(state, i));

   TaskVector positionVector;
   for(unsigned i=0;i<acquisitionSettings_.positionList.size();++i)
      positionVector.push_back(new PositionTask(state, i));

   TaskVector timeVector;
   for(unsigned i=0;i<acquisitionSettings_.timeSeries.size();++i)
      timeVector.push_back(new TimeTask(state, i));

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
   
   printf("allTasks.size() = %d", allTasks.size());
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

void MMAcquisitionEngine::runTest(AcquisitionSettings acquisitionSettings)
{
   acquisitionSettings.positionsFirst = true;
   acquisitionSettings.channelsFirst = true;

   core_->logMessage("running acquisition engine test...");
   MMAcquisitionSequencer sequencer(core_, coreCallback_, acquisitionSettings);
   TaskVector taskVector = sequencer.generateTaskVector();

   runner_.SetTasks(taskVector);
   runner_.Start();
}

bool MMAcquisitionEngine::isFinished()
{
   return runner_.IsFinished();
}