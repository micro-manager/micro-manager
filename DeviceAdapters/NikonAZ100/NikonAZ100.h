///////////////////////////////////////////////////////////////////////////////
// FILE:          NikonAZ100.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Nikon AZ100 adapter.
//
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu, 05/21/2008
// COPYRIGHT:     University of California San Francisco
// LICENSE:       This file contains materials that may be seen by Nikon as being part
//                of an NDA we signed.  Therefore, you may only see and edit this code
//                after permission from Nikon.
//
//                In all other cases, however, the LGPL applies to this code.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.


#ifndef _NIKON_TE2000_H_
#define _NIKON_TE2000_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "AZHub.h"
#include <string>                    
#include <map>
         
//////////////////////////////////////////////////////////////////////////////
// Error codes
//       
#define ERR_NOT_CONNECTED              10002          
#define ERR_UNKNOWN_POSITION           10003          
#define ERR_TYPE_NOT_DETECTED          10004          
#define ERR_EMPTY_ANSWER_RECEIVED      10005  
#define ERR_INVALID_CONTROL_PARAMATER  10006;
#define ERR_COMPUTER_CONTROL           10007;

class Hub : public CGenericBase<Hub> 
{        
public:  
   Hub();
   ~Hub();    
         
   // MMDevice API                   
   // ------------                   
   int Initialize();                 
   int Shutdown();                   
         
   void GetName(char* pszName) const;
   bool Busy();                      
         
   // action interface               
   // ----------------               
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);                 
   int OnControl(MM::PropertyBase* pProp, MM::ActionType eAct);                 

         
private: 
   bool initialized_;
   std::string name_; 
   std::string port_;
   std::string controlMode_;
};  


class FocusStage : public CStageBase<FocusStage>      
{
public:
   FocusStage();
   ~FocusStage();

   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();

   // Stage API
   virtual int SetPositionUm(double pos);             
   virtual int GetPositionUm(double& pos);            
   virtual double GetStepSize() const {return (double)stepSize_nm_/1000;}    
   virtual int SetPositionSteps(long steps) ;         
   virtual int GetPositionSteps(long& steps);         
   virtual int SetOrigin();    
   virtual int GetLimits(double& lower, double& upper)
   {    
      lower = lowerLimit_;     
      upper = upperLimit_;     
      return DEVICE_OK;        
   }
   // action interface         
   // ----------------         
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);             
   int OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct);             
        
private:
   int stepSize_nm_;           
   bool busy_;                 
   bool initialized_;          
   double lowerLimit_;         
   double upperLimit_;         
}; 


class Zoom : public CMagnifierBase<Zoom>      
{
public: 
   Zoom();
   ~Zoom();

   bool Busy();
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();

   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);             

   // Magnifier API
   double GetMagnification();

private:
   bool initialized_;
   double lowerLimit_;
   double upperLimit_;
};

class FilterBlock : public CStateDeviceBase<FilterBlock>
{
public:
   FilterBlock();
   ~FilterBlock();
  
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
   unsigned numPos_;
   std::string name_;
};

class Nosepiece : public CStateDeviceBase<Nosepiece>
{
public:
   Nosepiece();
   ~Nosepiece();
  
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
   unsigned numPos_;
   std::string name_;
};

#endif
