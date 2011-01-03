///////////////////////////////////////////////////////////////////////////////
// FILE:          CanonEDSDK.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
// ----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Canon EDSDK 
// COPYRIGHT:     USCF, 2010
// AUTHOR:        Nico Stuurman
// License:       LGPL
//
//


#ifndef CANONEDSDK_H_
#define CANONEDSDK_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../../3rdparty/trunk/Canon/EDSDK2.9/Mac/EDSDK/Header/EDSDKTypes.h"

//////////////////////////////////////////////////////////////////////////////
// Error codes

class CanonEDCamera : public CCameraBase<CanonEDCamera>
{
   public:
      CanonEDCamera();
      ~CanonEDCamera();

      // MMDevice API
      int Initialize();
      int Shutdown();

      void GetName(char* name) const;

      // MMCamera API
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
   int PrepareSequenceAcqusition() {return DEVICE_OK;}                       
   int StartSequenceAcquisition(double interval);                            
   int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);   
   int StopSequenceAcquisition();                                            
   int InsertImage();                                                        
  // int ThreadRun();                                                          
   bool IsCapturing();                                                       
 //  void OnThreadExiting() throw();                                           
   double GetNominalPixelSizeUm() const {return nominalPixelSizeUm_;}        
   double GetPixelSizeUm() const {return nominalPixelSizeUm_ * GetBinning();}
   int GetBinning() const;                                                   
   int SetBinning(int bS); 
   unsigned  GetNumberOfComponents() const { return nComponents_;};


   // action interface
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);              
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);            
   int OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct);             
   int OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct);          
   int OnCameraCCDXSize(MM::PropertyBase* , MM::ActionType );                
   int OnCameraCCDYSize(MM::PropertyBase* , MM::ActionType ); 

private:
   EdsError  getFirstCamera(EdsCameraRef  *camera);
   int EdsToMMError(EdsError err);
   static EdsError EDSCALLBACK handleObjectEvent( EdsObjectEvent event, EdsBaseRef  object, EdsVoid * context);
   static EdsError EDSCALLBACK  handlePropertyEvent (EdsPropertyEvent event, EdsPropertyID  property, EdsUInt32 param, EdsVoid * context);
   static EdsError EDSCALLBACK handleStateEvent (EdsStateEvent event, EdsUInt32 parameter, EdsVoid * context);

   static void* g_Self;

   double nominalPixelSizeUm_;
   unsigned nComponents_;
   bool isSDKLoaded_;
   bool isLegacy_;
   EdsCameraRef camera_;
   std::string cameraModel_;
};


#endif
