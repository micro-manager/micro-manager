///////////////////////////////////////////////////////////////////////////////
// FILE:          BiostepEagleIce.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
// ----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for biostep EagleIce
// COPYRIGHT:     biostep, 2013
// AUTHOR:        Jens Gläser
// License:       LGPL
//
//

#ifndef EagleIce_H_
#define EagleIce_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceThreads.h"
#include "../../../3rdparty/biostep/EI_SDK 1.0/EagleIceSDK.h"
#include <string>

class biThread;

class EagleIce : public CCameraBase<EagleIce>
{
public:
	EagleIce();
	~EagleIce();

	// MMDevice API
	int Initialize();
	int Shutdown();

	void GetName(char* name) const;

	// MMCamera API
	int SnapImage();     
	int InsertImage();
	const unsigned char* GetImageBuffer(){return m_img.GetPixels();}                                    
   unsigned GetImageWidth() const;                                           
   unsigned GetImageHeight() const;                                          
   unsigned GetImageBytesPerPixel() const{return m_img.Depth();};                                   
   unsigned GetBitDepth() const;                                             
   long GetImageBufferSize() const{return lastResolution.X*lastResolution.Y *GetImageBytesPerPixel();};                                          
   double GetExposure() const;                                               
   void SetExposure(double exp);                                             
   int SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize);       
   int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize);   
   int ClearROI();   
   int IsExposureSequenceable(bool& sequenceable) const { sequenceable = false; return DEVICE_OK;};
   int GetBinning() const;                                                   
   int SetBinning(int binF); 
   int StartSequenceAcquisition(double interval);
   int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
   int StopSequenceAcquisition();
   bool IsCapturing(); 
   int ThreadRun();
	EI_Resolution lastResolution;
	EI_Device* g_device;
	char buffer[2048*2048*2];
	ImgBuffer m_img;
	WORD imgx[2048*2048];
	bool _capturing;
	int OnCoolState(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTemp(MM::PropertyBase* pProp, MM::ActionType eAct);
	
private:
	int SetAllowedBinning();
	bool _camfound;	
	double* temp;
	EI_Status getFirstCam();
	biThread* thd_;
	
};

class biThread : public MMDeviceThreadBase
{
	friend class EagleIce;

public:
	biThread(EagleIce* pCam);
	~biThread();
	void Stop();
	void Start();
private:
	int svc(void) throw();
	EagleIce* camera_; 
	bool stop_; 
	MMThreadLock stopLock_; 
};

#endif
