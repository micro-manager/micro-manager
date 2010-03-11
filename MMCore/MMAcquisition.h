#include <vector>
#include <map>
#include <string>

#include "CoreCallback.h"

#include "MMRunnable.h"

class MultiAxisPosition : std::map<std::string, double>
{

};

struct Channel
{
   std::string name;
   double exposure;
   double zOffset;
   bool useZStack;
   int skipFrames;
};

typedef void (*voidFunc)();
typedef std::vector<MMRunnable *> TaskVector;

typedef std::vector<double> ZStack;
typedef std::vector<double> TimeSeries;
typedef std::vector<MultiAxisPosition> PositionList;
typedef std::vector<Channel> ChannelList;

struct AcquisitionSettings
{

   bool positionsFirst;
   bool channelsFirst;
   
   bool useTime;
   TimeSeries timeSeries;

   bool usePositions;
   PositionList positionList;

   bool useZStack;
   ZStack zStack;

   bool useChannels;
   ChannelList channelList;

   bool useAutofocus;
   std::string autofocusDevice;

   bool saveImages;

};


class MMAcquisitionRunner
{
private:
   TaskVector tasks_;
   void Run();

public:
   void Start();
   void Stop();
   void Pause();
   void Resume();
   void Step();



   void SetTasks(TaskVector tasks);
};

class MMAcquisitionSequencer
{
private:
   CoreCallback * coreCallback_;
   AcquisitionSettings acquisitionSettings_;
   TaskVector generateSlicesAndChannelsLoop();

public:
   MMAcquisitionSequencer(CoreCallback * coreCallback);
   TaskVector generateTaskVector();
   void setAcquisitionSettings(AcquisitionSettings settings);

};

class MMAcquisitionEngine
{
private:
   CMMCore * core_;
   CoreCallback * coreCallback_;
   MMAcquisitionSequencer * sequencer_;
   MMAcquisitionRunner * runner_;

public:
   MMAcquisitionEngine(CMMCore * core) { 
      core_ = core;
      coreCallback_ = new CoreCallback(core);
   }
   void runTest();
};