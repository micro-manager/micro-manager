///////////////////////////////////////////////////////////////////////////////
// FILE:       LeicaDMI.h
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Leica CAN bus adapater
//   
// COPYRIGHT:     100xImaging, Inc., 2008
// LICENSE:        
//                This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  

#ifndef _LEICA_H_
#define _LEICA_H_

#include "LeicaDMIScopeInterface.h"
#include "LeicaDMIModel.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceThreads.h"
#include <string>
#include <vector>
#include <map>

#define ERR_NOT_INITIALIZED 1001

class LeicaScope : public HubBase<LeicaScope>
{
   public:
      LeicaScope();
      ~LeicaScope();

      // Device API
      // ---------
      int Initialize();
      int Shutdown();
      void GetName(char* pszName) const;
      bool Busy();
      MM::DeviceDetectionStatus DetectDevice();

      // HUB interface
      // -------------
      int DetectInstalledDevices();

      // action interface                                                       
      // ----------------                                                       
      int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnMethod(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnAnswerTimeOut(MM::PropertyBase* pProp, MM::ActionType eAct); 

   private:
      bool initialized_;
      std::vector<std::string> discoveredDevices_;

      void AttemptToDiscover(int deviceCode, const char* deviceName);
      int GetNumberOfDiscoverableDevices();
      void GetDiscoverableDevice(int deviceNum, char *deviceName, unsigned int maxLength);
};


/*
 * Leica Incident light shutter
 */
class ILShutter : public CShutterBase<ILShutter>
{
public:
   ILShutter();
   ~ILShutter();

   int Initialize();
   int Shutdown();

   void GetName (char* pszName) const;
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
   std::string description_;
   bool state_;
   MM::MMTime changedTime_;
};

/*
 * Leica Transmitted light shutter
 */
class TLShutter : public CShutterBase<TLShutter>
{
public:
   TLShutter();
   ~TLShutter();

   int Initialize();
   int Shutdown();

   void GetName (char* pszName) const;
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
   std::string description_;
   bool state_;
   MM::MMTime changedTime_;
};


class ILTurret : public CStateDeviceBase<ILTurret>
{
public:
   ILTurret();
   ~ILTurret();

   // MMDevice API
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;};

   // action interface
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

protected:
   unsigned int numPos_;

private:
   bool initialized_;
   std::string name_;
   std::string description_;
   long pos_;
};

class ObjectiveTurret : public CStateDeviceBase<ObjectiveTurret>
{
public:
   ObjectiveTurret();
   ~ObjectiveTurret();

   // MMDevice API
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions() const {return numPos_;};

   // action interface
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnArticleNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnImmersion(MM::PropertyBase* pProp, MM::ActionType eAct);

protected:
   unsigned int numPos_;

private:
   bool initialized_;
   std::string name_;
   std::string description_;
   long pos_;
};


class FastFilterWheel : public CStateDeviceBase<FastFilterWheel>
{
public:
   FastFilterWheel();
   ~FastFilterWheel();

   // MMDevice API
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions() const {return numPos_;};

   // action interface
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFilterWheelID(MM::PropertyBase* pProp, MM::ActionType eAct);

protected:
   unsigned int numPos_;

private:
   bool initialized_;
   std::string name_;
   std::string description_;
   long filterWheelID_;
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
   int Home();
   int Stop();

   int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
   bool IsContinuousFocusDrive() const {return false;}

   int OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   std::string name_;
   std::string description_;
};

class XYStage : public CXYStageBase<XYStage>
{
public:
   XYStage();
   ~XYStage();

   // Device API
   // ---------
   int Initialize();
   int Shutdown();

   void GetName(char* pszName) const;
   bool Busy();

   // XYStage API                                                            
   // -----------
  //int SetPositionUm(double x, double y);
  //int SetRelativePositionUm(double x, double y);
  //int GetPositionUm(double& x, double& y);
  int SetPositionSteps(long x, long y);
  int SetRelativePositionSteps(long x, long y);
  int GetPositionSteps(long& x, long& y);
  int Home();
  int Stop();
  int SetOrigin();
  int SetAdapterOriginUm(double x, double y);
  int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
  double GetStepSizeXUm();
  double GetStepSizeYUm();
  int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
   int IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   // ----------------
   int OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int OnMirrorX(MM::PropertyBase* pProp, MM::ActionType eAct);
   //int OnMirrorY(MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   bool initialized_;
   std::string name_;
   std::string description_;
   long originXSteps_;
   long originYSteps_;
   //bool mirrorX_;
   //bool mirrorY_;
};

class Diaphragm : public CGenericBase<Diaphragm>
{
public:
   Diaphragm(LeicaDeviceModel* diaphragm, int deviceID, std::string name);
   ~Diaphragm();

   // MMDevice API
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;};

   // action interface
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   std::string name_;
   std::string description_;
   unsigned int numPos_;
   bool initialized_;
   LeicaDeviceModel* diaphragm_;
   int deviceID_;
};

class MagChanger : public CMagnifierBase<MagChanger>
{
public:
   MagChanger();
   ~MagChanger();

   // MMDevice API
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;};
   double GetMagnification();

   // action interface
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

protected:
   unsigned int numPos_;

private:
   bool initialized_;
   std::string name_;
   std::string description_;
};

class TLPolarizer : public CStateDeviceBase<TLPolarizer>
{
public:
   TLPolarizer();
   ~TLPolarizer();

   // MMDevice API
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;};

   // action interface
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

protected:
   unsigned int numPos_;

private:
   bool initialized_;
   std::string name_;
   std::string description_;
   long pos_;
};

class DICTurret : public CStateDeviceBase<DICTurret>
{
public:
   DICTurret();
   ~DICTurret();

   // MMDevice API
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;};

   // action interface
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPrismFinePosition(MM::PropertyBase* pProp, MM::ActionType eAct);

protected:
   unsigned int numPos_;

private:
   bool initialized_;
   std::string name_;
   std::string description_;
   long pos_;
   double finePos_;
};

class CondensorTurret : public CStateDeviceBase<CondensorTurret>
{
public:
   CondensorTurret();
   ~CondensorTurret();

   // MMDevice API
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;};

   // action interface
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

protected:
   unsigned int numPos_;

private:
   bool initialized_;
   std::string name_;
   std::string description_;
   long pos_;
};

// The incident light a.k.a the flourecense lamp is being derived
// from the shutter base. This is to make sure that for cases
// where there is no bright field shutter the flouroscent lamp
// can be switched off and on so that one can mix the bright-field
// and flourecense imaging in the same acquisition protocol
// -- Prashanth 26th Feb 2009

class TransmittedLight: public CShutterBase<TransmittedLight>
{
public:
	TransmittedLight();
	~TransmittedLight();

   int Initialize();
   int Shutdown();

   void GetName (char* pszName) const;
   bool Busy();

   // Shutter API
   int SetOpen (bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLevel(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnManual(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   std::string name_;
   std::string description_;
   bool state_;
   long level_;
   MM::MMTime changedTime_;
};

class AFC: public CAutoFocusBase<AFC>
{
public:
	AFC();
	~AFC();

   int Initialize();
   int Shutdown();

   void GetName (char* pszName) const;
   bool Busy();

   // AutoFocus API
   int SetContinuousFocusing(bool state);
   int GetContinuousFocusing(bool& state);
   bool IsContinuousFocusLocked();
   int FullFocus();
   int IncrementalFocus();
   int GetLastFocusScore(double& /*score*/) {return DEVICE_UNSUPPORTED_COMMAND;}
   int GetCurrentFocusScore(double& score);
   int GetOffset(double &offset);
   int SetOffset(double offset);
   int GetLEDIntensity(int &intensity);
   int SetLEDIntensity(int intensity);

   //Action Handlers
   int OnDichroicMirrorPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFullFocusTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLockThreshold(MM::PropertyBase* pProp,  MM::ActionType eAct);
   int OnLEDIntensity(MM::PropertyBase* pProp,  MM::ActionType eAct);

   bool initialized_;
   std::string name_;
   long timeOut_;
   long fullFocusTime_;
   double lockThreshold_;
   long LEDIntensity_;
};

class AFCOffset : public CStageBase<AFCOffset>
{
public:
   AFCOffset();
   virtual ~AFCOffset();

   virtual int Initialize();
   virtual int Shutdown();
   virtual void GetName(char* name) const;
   virtual bool Busy();
   virtual int GetPositionUm(double& microns);
   virtual int SetPositionUm(double microns);
   virtual int GetPositionSteps(long& steps);
   virtual int SetPositionSteps(long steps);
   virtual int SetOrigin() { return DEVICE_UNSUPPORTED_COMMAND; }
   virtual int GetLimits(double& /* lower */, double& /* upper */ ) { return DEVICE_UNSUPPORTED_COMMAND; }
   virtual bool IsContinuousFocusDrive() const { return true; }
   virtual int IsStageSequenceable(bool& flag) const { flag = false; return DEVICE_OK; }

private:
   bool initialized_;
   std::string name_;
};

class SidePort : public CStateDeviceBase<SidePort>
{
public:
   SidePort();
   ~SidePort();

   // MMDevice API
   int Initialize();
   int Shutdown();
    
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;};

   // action interface
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

protected:
   unsigned int numPos_;

private:
   bool initialized_;
   std::string name_;
   std::string description_;
};

#endif // _LeicaDMI_H_