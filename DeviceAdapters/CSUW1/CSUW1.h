///////////////////////////////////////////////////////////////////////////////
// FILE:       CSUW1.h
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Yokogawa CSUW1 adapter
//
// COPYRIGHT:     University of California, San Francisco, 2006
//                All rights reserved
//
// LICENSE:       This library is free software; you can redistribute it and/or
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
//                                                                                     
// AUTHOR: Nico Stuurman, 02/02/2007                                                 
//                                                                                   
// Based on NikonTE2000 controller adapter by Nenad Amodaj                           


#ifndef _CSUW1_H_
#define _CSUW1_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_COMMAND          10002
#define ERR_UNKNOWN_POSITION         10003
#define ERR_HALT_COMMAND             10004
#define ERR_NEGATIVE_ANSWER          10005

class Hub : public CGenericBase<Hub>
{
public:
   Hub();
   ~Hub();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   // MMCore name of serial port
   std::string port_;
   // Command exchange with MMCore
   std::string command_;
};


class FilterWheel : public CStateDeviceBase<FilterWheel>
{
public:
   FilterWheel();
   ~FilterWheel();                                                             
                                                                             
   // MMDevice API                                                           
   // ------------                                                           
   int Initialize();                                                         
   int Shutdown();                                                           
                                                                             
   void GetName(char* pszName) const;                                        
   bool Busy();                                                              
   unsigned long GetNumberOfPositions()const {return numPos_;}               
                                                                             
   // action interface                                                       
   // ----------------                                                       
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);                
   int OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);                
   int OnWheelNr(MM::PropertyBase* pProp, MM::ActionType eAct);
                                                                             
private:                                                                     
   MM::MMTime lastMoveTime_;
   long posMoved_;
   long wheelNr_;
   bool initialized_;                                                        
   unsigned numPos_;                                                         
   long  pos_;
   long speed_;
   std::string name_;                                                        
};


class Dichroic : public CStateDeviceBase<Dichroic>
{
public:
   Dichroic();
   ~Dichroic();
   //
   // MMDevice API 
   // ------------ 
   int Initialize();
   int Shutdown();
                                                                             
   void GetName(char* pszName) const; 
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ---------------- 
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   long pos_;
   std::string name_;
   unsigned numPos_; 
};


class Shutter : public CShutterBase<Shutter>
{
public:
   Shutter();
   ~Shutter();

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
   MM::MMTime changedTime_;
   bool initialized_;
   std::string name_;
   bool isOpen_;
};


class DriveSpeed : public CGenericBase<DriveSpeed>
{
public:
   DriveSpeed();
   ~DriveSpeed();
   //
   // MMDevice API 
   // ------------ 
   int Initialize();
   int Shutdown();
                                                                             
   void GetName(char* pszName) const; 
   bool Busy();

   // action interface
   // ---------------- 
   int OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRun(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool running_;

   bool initialized_;
   std::string name_;
};

class BrightField : public CStateDeviceBase<BrightField>
{
public:
   BrightField();
   ~BrightField();
   //
   // MMDevice API 
   // ------------ 
   int Initialize();
   int Shutdown();
                                                                             
   void GetName(char* pszName) const; 
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ---------------- 
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   std::string name_;
   unsigned numPos_;
};

class Disk : public CStateDeviceBase<Disk>
{
public:
   Disk();
   ~Disk();
   //
   // MMDevice API 
   // ------------ 
   int Initialize();
   int Shutdown();
                                                                             
   void GetName(char* pszName) const; 
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ---------------- 
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   std::string name_;
   unsigned numPos_;
};

class Port : public CStateDeviceBase<Port>
{
public:
   Port();
   ~Port();
   //
   // MMDevice API 
   // ------------ 
   int Initialize();
   int Shutdown();
                                                                             
   void GetName(char* pszName) const; 
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ---------------- 
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   std::string name_;
   unsigned numPos_;
};

class Aperture : public CStateDeviceBase<Aperture>
{
public:
   Aperture();
   ~Aperture();
   //
   // MMDevice API 
   // ------------ 
   int Initialize();
   int Shutdown();
                                                                             
   void GetName(char* pszName) const; 
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ---------------- 
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   std::string name_;
   unsigned numPos_;
};

class Frap : public CStateDeviceBase<Frap>
{
public:
   Frap();
   ~Frap();
   //
   // MMDevice API 
   // ------------ 
   int Initialize();
   int Shutdown();
                                                                             
   void GetName(char* pszName) const; 
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ---------------- 
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   std::string name_;
   unsigned numPos_;
};

class Magnifier : public CStateDeviceBase<Magnifier>
{
public:
   Magnifier();
   ~Magnifier();
   //
   // MMDevice API 
   // ------------ 
   int Initialize();
   int Shutdown();
                                                                             
   void GetName(char* pszName) const; 
   bool Busy();
   unsigned long GetNumberOfPositions()const {return numPos_;}

   // action interface
   // ---------------- 
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMagnifierNr(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   bool initialized_;
   long nr_;
   std::string name_;
   unsigned numPos_;
};

class NIRShutter : public CShutterBase<NIRShutter>
{
public:
   NIRShutter();
   ~NIRShutter();

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
   MM::MMTime changedTime_;
   bool initialized_;
   std::string name_;
   bool isOpen_;
};

#endif //_CSUW1_H_
