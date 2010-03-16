#include <vector>
#include <map>
#include <string>

#include "CoreCallback.h"

#include "MMRunnable.h"

class MultiAxisPosition
{
public:
   std::map<std::string,double> singleAxisPositions;
   std::map<std::string,pair<double,double>> doubleAxisPositions;

   void AddOneSingleAxisPosition(string name, double pos)
   {
      singleAxisPositions[name] = pos;
   }

   void AddDoubleAxisPosition(string name, double posX, double posY)
   {
      doubleAxisPositions[name] = pair<double,double>(posX,posY);
   }
};

class Channel
{

public:
   std::string group;
   std::string name;
   double exposure;
   double zOffset;
   bool useZStack;
   int skipFrames;

   Channel() { }

   Channel(std::string _group, std::string _name, double _exposure,
      double _zOffset = 0.0, bool _useZStack = true, int _skipFrames = 0)
   {
      group = _group;
      name = _name;
      exposure = _exposure;
      zOffset = _zOffset;
      useZStack = _useZStack;
      skipFrames = _skipFrames;
   }
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
   MMAcquisitionSequencer * sequencer_;
   MMAcquisitionRunner * runner_;

public:
   MMAcquisitionEngine(CMMCore * core) { 
      core_ = core;
      coreCallback_ = new CoreCallback(core);
   }
   void runTest();
};