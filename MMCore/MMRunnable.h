#include <vector>
#include <map>
#include <string>



#ifndef ACQUISITION_TYPES_H
#define ACQUISITION_TYPES_H

using namespace std;

class MMRunnable
{
public:
   enum RunnableType {OTHER, TIME, POSITION, CHANNEL, SLICE, IMAGE, AUTOFOCUS};

   RunnableType type;

   virtual ~MMRunnable() {};
   virtual void run() = 0;

};


typedef pair<double, double> point2D;

class MultiAxisPosition
{
public:
   string name;
   map<string,double> singleAxisPositions;
   map<string,point2D> doubleAxisPositions;


   void AddSingleAxisPosition(string name, double pos)
   {
      singleAxisPositions[name] = pos;
   }

   void AddDoubleAxisPosition(string name, double posX, double posY)
   {
      doubleAxisPositions[name] = point2D(posX,posY);
   }

   void GetSingleAxisPosition(string name, double& pos)
   {
      pos = singleAxisPositions[name];
   }

   void GetDoubleAxisPosition(string name, double& posX, double& posY)
   {
      posX = doubleAxisPositions[name].first;
      posY = doubleAxisPositions[name].second;
   }

   string GetName()
   {
	   return name;
   }
};

class Channel
{

public:
   string group;
   string name;
   double exposure;
   double zOffset;
   bool useZStack;
   int skipFrames;

   Channel() { }

   Channel(string _group, string _name, double _exposure,
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
typedef vector<MMRunnable *> TaskVector;

typedef vector<double> ZStack;
typedef vector<double> TimeSeries;
typedef vector<MultiAxisPosition> PositionList;
typedef vector<Channel> ChannelList;

class AcquisitionSettings
{

public:
   bool positionsFirst;
   bool channelsFirst;
   
   TimeSeries timeSeries;
   PositionList positionList;
   ZStack zStack;
   ChannelList channelList;

   bool useAutofocus;
   string autofocusDevice;
   int autofocusSkipFrames;

   bool saveImages;
   bool keepShutterOpenSlices;
   bool keepShutterOpenChannels;

   AcquisitionSettings():
      positionsFirst(true),
      channelsFirst(true),
      useAutofocus(false),
      saveImages(false),
      autofocusDevice("")
   {}

};

#endif // ACQUISITION_TYPES_H