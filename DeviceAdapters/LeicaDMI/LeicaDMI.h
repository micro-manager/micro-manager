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


class LeicaScope : public CGenericBase<LeicaScope>
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
      
      // action interface                                                       
      // ----------------                                                       
      int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnMethod(MM::PropertyBase* pProp, MM::ActionType eAct); 
      int OnAnswerTimeOut(MM::PropertyBase* pProp, MM::ActionType eAct); 

   private:
      bool initialized_;
      std::string port_;
      double answerTimeoutMs_;
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
   long pos_;
   std::string name_;
   std::string description_;
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

protected:
   unsigned int numPos_;

private:
   bool initialized_;
   long pos_;
   std::string name_;
   std::string description_;
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
  int SetPositionUm(double x, double y);
  int SetRelativePositionUm(double x, double y);
  int GetPositionUm(double& x, double& y);
  int SetPositionSteps(long x, long y);
  int SetRelativePositionSteps(long x, long y);
  int GetPositionSteps(long& x, long& y);
  int Home();
  int Stop();
  int SetOrigin();
  int SetAdapterOriginUm(double x, double y);
  int GetLimits(double& xMin, double& xMax, double& yMin, double& yMax);

   // action interface
   // ----------------
   int OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMirrorX(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMirrorY(MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   bool busy_;
   bool initialized_;
   std::string name_;
   std::string description_;
   long originXSteps_;
   long originYSteps_;
   bool mirrorX_;
   bool mirrorY_;
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
   bool initialized_;
   std::string name_;
   std::string description_;
   unsigned int numPos_;
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
   long pos_;
   std::string name_;
   std::string description_;
};

#endif // _LeicaDMI_H_
