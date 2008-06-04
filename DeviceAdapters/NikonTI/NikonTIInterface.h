#ifndef _NIKONTIINTERFACE_H_
#define _NIKONTIINTERFACE_H_

#include <string>
#include <vector>

class TIObjective
{
public:
   virtual std::string GetDescription() = 0;
   virtual std::string GetMagnification() = 0;
   virtual std::string GetModel() = 0;
   virtual std::string GetName() = 0;
   virtual long GetUsePFS() = 0;
   virtual std::string GetType() = 0;
   virtual std::string GetUsage() = 0;
   virtual long GetWDType() = 0;
   virtual double GetNA() = 0;
};

class TIFilterBlock
{
public:
   virtual std::string GetName() = 0;
   // TODO: Add support for all other filter data
};

class TIDevice
{
public:
   // access functions to device model  status
   virtual bool GetIsMounted() = 0;
   virtual long GetLowerLimit()  = 0;
   virtual long GetUpperLimit() = 0;
   virtual int GetValue(long& value) = 0;
   virtual int SetValue(long value) = 0;
   virtual bool Busy() = 0;

   // update model with current status of the device in the microscope
   virtual int UpdateIsMounted () = 0;
   virtual int UpdateValue() = 0;
   virtual int UpdateValueRange() = 0;
};

class TIShutterDevice : public virtual TIDevice
{
public:
	virtual bool IsOpen() = 0;
	virtual int Open() = 0;
	virtual int Close() = 0;
};

class TIPositionDevice : public virtual TIDevice
{
public:
   virtual int GetPosition(long& position) = 0;
   // Application sets new position 
   virtual int SetPosition(long position) = 0;
   // Microcope updates model with current position
   virtual int UpdatePosition() = 0;
   virtual int UpdatePositionRange() = 0;

   virtual std::vector<std::string> GetLabels() = 0;
};

class TIDrive : public virtual TIDevice 
{
public:
   virtual int GetPosition(long& position) = 0;
   // Application sets new position 
   virtual int SetPosition(long position) = 0;
   // Microcope updates model with current position
   virtual int UpdatePosition() = 0;
   virtual int MoveAbsolute(long position) = 0;
   virtual int MoveRelative(long position) = 0;
};

class TIZDrive : public virtual TIDrive
{
   virtual int GetSpeed (long& speed) = 0;
   virtual int SetSpeed (long speed) = 0;
   virtual int UpdateSpeed () = 0;
   virtual int GetTolerance(long& tolerance) = 0;
   virtual int SetTolerance (long tolerance) = 0;
};

class TIPFSStatus : public virtual TIDevice
{
public:
	virtual bool IsEnabled() = 0;
	virtual int Enable() = 0;
	virtual int Disable() = 0;
};

class TIPFSOffset : public virtual TIDrive
{
// Not sure if there are any methods specific to the PFS that are not part of the deneric TIDrive
// I thougt to make the destractor pure virtual just to prevent creation of TIPFSOffset instances
// buts its not really working... 

//	virtual ~TIPFSOffset() = 0;
};

class TIEpiShutter : public virtual TIShutterDevice
{
// no shutter specific methods, this is mostly to maintain proper OO design
};

class TIDiaShutter : public virtual TIShutterDevice
{
// no shutter specific methods, this is mostly to maintain proper OO design
};

class TIAuxShutter : public virtual TIShutterDevice
{
// no shutter specific methods, this is mostly to maintain proper OO design
};

class TIXDrive : public virtual TIDrive
{
   virtual int GetSpeed (long& speed) = 0;
   virtual int SetSpeed (long speed) = 0;
   virtual int UpdateSpeed () = 0;
   virtual int GetTolerance(long& tolerance) = 0;
   virtual int SetTolerance (long tolerance) = 0;
};

class TIYDrive : public virtual TIDrive
{
   virtual int GetSpeed (long& speed) = 0;
   virtual int SetSpeed (long speed) = 0;
   virtual int UpdateSpeed () = 0;
   virtual int GetTolerance(long& tolerance) = 0;
   virtual int SetTolerance (long tolerance) = 0;
};

class TINosepiece : public virtual TIPositionDevice
{
public:
   virtual std::vector<TIObjective*> GetObjectives() = 0;
};

class TIFilterBlockCassette : public virtual TIPositionDevice
{
public:
   ;
   //virtual std::vector<TIObjective*> GetObjectives() = 0;
};

class TILightPath : public virtual TIPositionDevice
{
};

class TIModel
{
public:
   //TIModel();
   //~TIModel();

   virtual int Initialize() = 0;
   virtual int Shutdown() = 0;
   virtual bool IsInitialized() = 0;

   TINosepiece* nosepiece_;
   TIFilterBlockCassette* filterBlock1_;
   TIFilterBlockCassette* filterBlock2_;
   TIZDrive* zDrive_;
   TIYDrive* yDrive_;
   TIXDrive* xDrive_;
   TIPFSOffset* pfsOffset_;
   TIPFSStatus* pfsStatus_;
   TIEpiShutter* pEpiShutter_;
   TIDiaShutter* pDiaShutter_;
   TIAuxShutter* pAuxShutter_;
   TILightPath* pLightPath_;
};

#endif