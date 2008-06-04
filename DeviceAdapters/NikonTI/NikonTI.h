///////////////////////////////////////////////////////////////////////////////
// FILE:       NikonTI.h
// PROJECT:    MicroManager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Nikon TI microscope adpater
//   
// COPYRIGHT:     University of California, San Francisco, 2008
// LICENSE:       This code has been developd using information provided by Nikon under a non-disclosure
//                agreement.  Therefore, this code can not be made publicly available unless Nikon provides permission to do so
//                It is the intend of the author to provide this code with the LGPL License once allowed by Nikon
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu 4/10/2008

#ifndef _NIKONTI_H_
#define _NIKONTI_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceThreads.h"
#include "NikonTIInterface.h"

#include <string>
#include <vector>
#include <map>


/////////////////////////////////////////////////////////////////////////////
// Device classed
class NikonTIHub : public CGenericBase<NikonTIHub>
{
public:
   NikonTIHub();
   ~NikonTIHub();

   // Device API
   // ---------
   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy();

};

class NosePiece : public CStateDeviceBase<NosePiece>
{
public:
   NosePiece();
   ~NosePiece();

   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;};

   // action interface
   // ---------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnValue(MM::PropertyBase* pProp, MM::ActionType eAct);

protected:
   unsigned int numPos_;
   std::string name_;
};

class FilterBlock : public CStateDeviceBase<FilterBlock>
{
public:
   FilterBlock(int blockNumber);
   ~FilterBlock();

   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;};

   // action interface
   // ---------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

protected:
   TIFilterBlockCassette* pFilterBlock_;
   unsigned int numPos_;
   std::string name_;
};

class LightPath : public CStateDeviceBase<LightPath>
{
public:
   LightPath();
   ~LightPath();

   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;};

   // action interface
   // ---------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

protected:
   TILightPath* pLightPath_;
   unsigned int numPos_;
   std::string name_;
};

class PFSOffset : public CStageBase<PFSOffset>
{
public:
	PFSOffset();
	~PFSOffset();

// Device API
   bool Busy();
   int Initialize();
   int Shutdown ();
   void GetName(char* name) const;
   int SetPositionUm(double position);
   int GetPositionUm(double& position);
   int SetPositionSteps(long steps);
   int GetPositionSteps(long& steps);
   int SetOrigin();
   int GetLimits(double& lower, double& upper);

   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	double factor_; 
	std::string name_;

};

class PFSStatus : public CAutoFocusBase<PFSStatus>
{
public:
   PFSStatus();
   ~PFSStatus();
      
   // MMDevice API
   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();

   // AutoFocus API
   virtual int SetContinuousFocusing(bool state);
   virtual int GetContinuousFocusing(bool& state);
   virtual int Focus();
   virtual int GetFocusScore(double& /*score*/) {return DEVICE_UNSUPPORTED_COMMAND;}
   virtual bool IsContinuousFocusLocked();

private:
   bool initialized_;
   std::string name_;
};


class ZDrive : public CStageBase<ZDrive>
{
public:
   ZDrive();
   ~ZDrive();

   // Device API
   bool Busy();
   int Initialize();
   int Shutdown ();
   void GetName(char* name) const;
   int SetPositionUm(double position);
   int GetPositionUm(double& position);
   int SetPositionSteps(long steps);
   int GetPositionSteps(long& steps);
   int SetOrigin();
   int GetLimits(double& lower, double& upper);

   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   double factor_;
   std::string name_;

};

class XYDrive : public CXYStageBase<XYDrive>
{
public:
   XYDrive();
   ~XYDrive();
  
    // Device API
   bool Busy();
   int Initialize();
   int Shutdown ();
   void GetName(char* name) const;
   int SetPositionUmX(double position);
   int GetPositionUmX(double& position);
   int SetPositionUmY(double position);
   int GetPositionUmY(double& position);
   int SetPositionUm(double x,double y);
   int GetPositionUm(double& x,double& y);
   int SetPositionStepsX(long steps);
   int GetPositionStepsX(long& steps);
   int SetPositionStepsY(long steps);
   int GetPositionStepsY(long& steps);
   int SetPositionSteps(long xsteps,long ysteps);
   int GetPositionSteps(long& xsteps,long& ysteps);
   int SetOrigin();
   int GetLimits(double& lowerx, double& upperx,double& lowery, double& uppery);
   int Stop();
   int Home();

   int OnPositionX(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPositionY(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
	std::string name_;
	double factor_;
};

class EpiShutter : public CShutterBase<EpiShutter>
{
public:

	EpiShutter();
   ~EpiShutter();


   // Device API
   // ---------
   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy();

    // Shutter API
   int SetOpen (bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
  
private:
   bool initialized_;
   std::string name_;
   unsigned shutterNr_;
   bool state_;
};

class DiaShutter : public CShutterBase<DiaShutter>
{
public:

	DiaShutter();
   ~DiaShutter();


   // Device API
   // ---------
   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy();

    // Shutter API
   int SetOpen (bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
  
private:
   bool initialized_;
   std::string name_;
   unsigned shutterNr_;
   bool state_;
};

class AuxShutter : public CShutterBase<AuxShutter>
{
public:

	AuxShutter();
   ~AuxShutter();


   // Device API
   // ---------
   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy();

    // Shutter API
   int SetOpen (bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
  
private:
   bool initialized_;
   std::string name_;
   unsigned shutterNr_;
   bool state_;
};	

#endif