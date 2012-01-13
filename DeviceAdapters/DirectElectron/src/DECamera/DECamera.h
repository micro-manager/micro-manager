///////////////////////////////////////////////////////////////////////////////
// FILE:          DECamera.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Direct Electron Camera plugin
// AUTHOR:        Sunny Chow, sunny.chow@acm.org, 07/28/2010

#ifndef _DECAMERA_H_
#define _DECAMERA_H_

#define WIN32_LEAN_AND_MEAN		

#include "MMDevice/DeviceBase.h"
#include "MMDevice/ImgBuffer.h"
#include "MMDevice/DeviceThreads.h"
#include <string>
#include <map>
#include "DEProtoProxy.h"

using namespace DirectElectronPlugin;
//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103



//////////////////////////////////////////////////////////////////////////////
// CDECamera class
// Simulation of the Camera device
//////////////////////////////////////////////////////////////////////////////
class CDECamera : public CCameraBase<CDECamera>  
{
public:
   // Packet types
   struct IntPair
   {
      int x;
      int y;
   };

   struct FloatPair
   {
      float x;
      float y;
   };
   
   CDECamera();
   ~CDECamera();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
   void GetName(char* name) const;      
   
   // MMCamera API
   // ------------
   int SnapImage();
   const unsigned char* GetImageBuffer();
   unsigned GetImageWidth() const;
   unsigned GetImageHeight() const;
   unsigned GetImageBytesPerPixel() const;
   unsigned GetBitDepth() const;
   long GetImageBufferSize() const;
   double GetExposure() const;
   void SetExposure(double exp);
   int SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize); 
   int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize); 
   int ClearROI();
   virtual double GetNominalPixelSizeUm() const;
   virtual double GetPixelSizeUm() const;
   int GetBinning() const;
   int SetBinning(int binSize);

   // Consider putting into a different class
   static MM::PropertyType convertType(const PropertyHelper::PropertyType& type);

   // Sequence control functions.
   //virtual int StartSequenceAcquisition(double interval);
   //virtual int StopSequenceAcquisition();
   virtual int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
   virtual int StopSequenceAcquisition();
   virtual int ThreadRun();
   virtual int IsExposureSequenceable(bool& isSequenceable) const;

   // action interface
   // ----------------   
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnProperty(MM::PropertyBase* pProp, MM::ActionType eAct);

   int SetPropertyVal(string name, int val);
   int GetPropertyVal(string name, int& out);
private:
   int SetAllowedBinning();

   // internal function to setup and capture a single frame.
   void SnapSingleFrame_();
   void SetupCapture_();

   // utility function
   inline int BoostToMMError(const std::exception& e);

   int InitializeProperties();
   void SetupProperty(string, PropertyHelper settings);
   ImgBuffer img_;
   bool initialized_;
   bool initializedProperties_;
   double readoutUs_;
   MM::MMTime readoutStartTime_;
   long scanMode_;
   int bitDepth_;   
   string camera_name_;
   bool camera_supports_binning_; 
   IntPair current_roi_offset_; 
   int current_binning_factor_; 
   
   int ResizeImageBuffer();

   DEProtoProxy* proxy_;
   int sensorSizeX_;
   int sensorSizeY_;
   double exposureTime_;   
   bool exposureEnabled_;
   FloatPair pixelSize_;   

   string lastLabel_;
   map<MM::PropertyBase*, string> reverseLookup_;

   // To allow changing of parameters while acquiring.
   boost::mutex acqmutex_;
};

#endif //_DECAMERA_H_
