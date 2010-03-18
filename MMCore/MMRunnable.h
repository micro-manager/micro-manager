#include <vector>
#include <map>
#include <string>


class MMRunnable
{
public:
   enum RunnableType {OTHER, TIME, POSITION, CHANNEL, SLICE, IMAGE, AUTOFOCUS};

   RunnableType type;

   virtual ~MMRunnable() {};
   virtual void run() = 0;

};


typedef std::pair<double, double> point2D;

class MultiAxisPosition
{
public:
   std::map<std::string,double> singleAxisPositions;
   std::map<std::string,point2D> doubleAxisPositions;

   void AddOneSingleAxisPosition(std::string name, double pos)
   {
      singleAxisPositions[name] = pos;
   }

   void AddDoubleAxisPosition(std::string name, double posX, double posY)
   {
      doubleAxisPositions[name] = point2D(posX,posY);
   }

   void GetSingleAxisPosition(std::string name, double& pos)
   {
      pos = singleAxisPositions[name];
   }

   void GetDoubleAxisPosition(std::string name, double& posX, double& posY)
   {
      posX = doubleAxisPositions[name].first;
      posY = doubleAxisPositions[name].second;
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
