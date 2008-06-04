#ifndef _NIKONTICOMINTERFACE_H_H
#define _NIKONTICOMINTERFACE_H_H

#include "NikonTIInterface.h"
#import "C:\Program Files\Nikon\Shared\Bin\NikonTi.dll"named_guids
#import "C:\Program Files\Nikon\Shared\Bin\MipParam2.dll"named_guids
#include "AtlBase.h" // for AtlAdvise
#include <assert.h>
#include "MipParamEventImpl.h"

#pragma warning ( disable : 4250 ) // disables warnings concerning dominance

class TICOMObjective : public TIObjective
{
public:
   TICOMObjective();

   // implementation of abstract class
   std::string GetDescription() {return description_;};
   std::string GetMagnification() {return magnification_;};
   std::string GetModel() {return model_;};
   std::string GetName() {return name_;};
   long GetUsePFS() {return usePFS_;};
   std::string GetType() {return type_;};
   std::string GetUsage() {return usage_;};
   long GetWDType() {return typeWD_;};
   double GetNA() {return na_;};
   bool IsMounted() {return isMounted_;};
   
   int UpdateData(TISCOPELib::IObjectivePtr objective);
protected:
   std::string description_;
   std::string magnification_;
   std::string model_;
   std::string name_;
   double na_;
   long usePFS_;
   std::string type_;
   std::string usage_;
   long typeWD_; 
   bool isMounted_;
};

class TICOMFilterBlock : public TIFilterBlock
{
public:
   TICOMFilterBlock() {;};
   std::string GetName() {return name_;};

   int UpdateData(TISCOPELib::IFilterBlockPtr pFilterBlock);
private:
   std::string name_;
};

class TICOMDevice : public virtual TIDevice, public CParamEventSinkImpl
{
public:
   // access functions to device model  status
   bool GetIsMounted() {return isMounted_;};
   virtual long GetLowerLimit() {return lowerLimit_;};
   virtual long GetUpperLimit() {return upperLimit_;};
   virtual int GetValue(long& value) {UpdateValue(); value = value_; return 0;};
   virtual int SetValue(long value);
   bool Busy();

   // Implementation of CParamEvenSinkImpl
   HRESULT __stdcall OnValueChanged();

   // update model with current status of the device in the microscope
   int UpdateIsMounted();
   int UpdateValue();
   int UpdateValueRange();

   virtual TISCOPELib::IScopeAccessory* GetTICOMDevice() = 0;
   //virtual MIPPARAMLib::IMipParameterPtr pValue = 0;
   virtual TISCOPELib::INikonTiPtr GetMicroscope() = 0;

private:
   long type_; 
   std::string name_;
   std::string unit_;
   long resolution_;
   bool isMounted_;
   long value_;
   long lowerLimit_;
   long upperLimit_;
};

class TICOMPositionDevice : public virtual TICOMDevice, public virtual TIPositionDevice
{
public:
   // Application enquires about current position
   int GetPosition(long& position) {UpdatePosition(); position = position_; return 0;};
   // Application sets new position 
   int SetPosition(long position);
   // Microcope updates model with current position
   int UpdatePosition();
   int UpdatePositionRange();

   // Access functions for derived classes
   long PositionLowerLimit() {return positionLowerLimit_;};
   long PositionUpperLimit() {return positionUpperLimit_;};

   virtual TISCOPELib::ITurretAccessory* GetTICOMPositionDevice() = 0;
private:
   long position_;
   long positionLowerLimit_;
   long positionUpperLimit_;
};

class TICOMShutterDevice : public virtual TICOMDevice, public virtual TIShutterDevice
{
public: 
	// Application gets the current status: true=open, false=close
	bool IsOpen();
	int Open();
	int Close();
	virtual TISCOPELib::IShutterAccessory* GetTICOMShutterDevice() = 0;

};

class TICOMDrive : public virtual TICOMDevice, public virtual TIDrive 
{
public:
   int GetPosition(long& position) {UpdatePosition(); position = position_; return 0;};
   // Application sets new position 
   int SetPosition(long position);
   // Microcope updates model with current position
   int UpdatePosition();
   int UpdatePositionRange();
   int MoveAbsolute(long position);
   int MoveRelative(long position);

    // Access functions for derived classes
   long PositionLowerLimit() {return positionLowerLimit_;};
   long PositionUpperLimit() {return positionUpperLimit_;};

   // Implementation of CParamEvenSinkImpl
   HRESULT __stdcall OnValueChanged();

   virtual TISCOPELib::IPositionAccessory* GetTICOMDrive() = 0;
private:
   long position_;
   long positionLowerLimit_;
   long positionUpperLimit_;
};

class TICOMEpiShutter : public virtual TIEpiShutter, public virtual TICOMShutterDevice
{
public: 
	int Initialize();
	//int Open();
	//int Close();
	//bool IsOpen();
 
   // Pointer to COM EpiShutter object
   TISCOPELib::IEpiShutter *pEpiShutter_;
   // Pointer to the global microscope object
   TISCOPELib::INikonTiPtr pMicroscope_;

   TISCOPELib::IScopeAccessory *TICOMDevice::GetTICOMDevice() {return pEpiShutter_;};
   TISCOPELib::IShutterAccessory* TICOMShutterDevice::GetTICOMShutterDevice() {return pEpiShutter_;};
   TISCOPELib::INikonTiPtr GetMicroscope() {return pMicroscope_;};

private:
   long type_; 
   std::string name_;
	
};

class TICOMDiaShutter : public virtual TIDiaShutter, public virtual TICOMShutterDevice
{
public: 
	int Initialize();
	//int Open();
	//int Close();
	//bool IsOpen();
 
   // Pointer to COM EpiShutter object
   TISCOPELib::IDiaShutter *pDiaShutter_;
   // Pointer to the global microscope object
   TISCOPELib::INikonTiPtr pMicroscope_;

   TISCOPELib::IScopeAccessory *TICOMDevice::GetTICOMDevice() {return pDiaShutter_;};
   TISCOPELib::IShutterAccessory* TICOMShutterDevice::GetTICOMShutterDevice() {return pDiaShutter_;};
   TISCOPELib::INikonTiPtr GetMicroscope() {return pMicroscope_;};

private:
   long type_; 
   std::string name_;
	
};

class TICOMAuxShutter : public virtual TIAuxShutter, public virtual TICOMShutterDevice
{
public: 
	int Initialize();
 
   // Pointer to COM EpiShutter object
   TISCOPELib::IAuxShutter *pAuxShutter_;
   // Pointer to the global microscope object
   TISCOPELib::INikonTiPtr pMicroscope_;

   TISCOPELib::IScopeAccessory *TICOMDevice::GetTICOMDevice() {return pAuxShutter_;};
   TISCOPELib::IShutterAccessory* TICOMShutterDevice::GetTICOMShutterDevice() {return pAuxShutter_;};
   TISCOPELib::INikonTiPtr GetMicroscope() {return pMicroscope_;};

private:
   long type_; 
   std::string name_;
	
};

class TICOMZDrive : public virtual TIZDrive, public virtual TICOMDrive 
{
   //TICOMZDrive(TISCOPELib::IZDrive* pDrive) {pDrive_ = pDrive;};
public:
   int Initialize();

   int GetSpeed (long& speed) {UpdateSpeed(); speed = speed_; return 0;};
   int SetSpeed (long speed);
   int UpdateSpeed ();
   //int UpdateSpeed ();
   int GetTolerance(long& tolerance);
   int SetTolerance (long tolerance);

   // Pointer to COM Drive object
   TISCOPELib::IZDrive* pZDrive_;
   // Pointer to the global microscope object
   TISCOPELib::INikonTiPtr pMicroscope_;

   TISCOPELib::IScopeAccessory *TICOMDevice::GetTICOMDevice() {return pZDrive_;};
   TISCOPELib::IPositionAccessory* TICOMDrive::GetTICOMDrive() 
   {
      return pZDrive_;
   };
   TISCOPELib::INikonTiPtr GetMicroscope() {return pMicroscope_;};
  
protected:
   bool isMounted_;
   long lowerLimit_;
   long upperLimit_;

private:
   long speed_;
   long resolution_;
   std::vector<std::string> labels_;
};


class TICOMPFSOffset : public virtual TIPFSOffset, public virtual TICOMDrive 
{
public:
   int Initialize();

   // Pointer to COM Drive object
   TISCOPELib::IPFS *pPFS_;
   // Pointer to the global microscope object
   TISCOPELib::INikonTiPtr pMicroscope_;

   TISCOPELib::IScopeAccessory *TICOMDevice::GetTICOMDevice() {return pPFS_;};
   TISCOPELib::IPositionAccessory* TICOMDrive::GetTICOMDrive() 
   {
      return pPFS_;
   };
   TISCOPELib::INikonTiPtr GetMicroscope() {return pMicroscope_;};

	~TICOMPFSOffset();
  
protected:
   bool isMounted_;
   long lowerLimit_;
   long upperLimit_;

};

class TICOMPFSStatus : public virtual TIPFSStatus, public virtual TICOMDevice
{
public: 
	int Initialize();
	bool IsEnabled();
	int Enable();
	int Disable();

	// Pointer to the COM Device object
	TISCOPELib::IPFS* pPFS_;
	// Pointer to the global microscope object
    TISCOPELib::INikonTiPtr pMicroscope_;
	// quick implementation of a few TICOMDevice method
    TISCOPELib::IScopeAccessory *TICOMDevice::GetTICOMDevice() {return pPFS_;};
    TISCOPELib::INikonTiPtr GetMicroscope() {return pMicroscope_;};

protected:
   bool isMounted_;

private:
	std::string name_;

};



class TICOMXDrive : public virtual TIXDrive, public virtual TICOMDrive 
{
public:
   int Initialize();

   int GetSpeed (long& speed) {UpdateSpeed(); speed = speed_; return 0;};
   int SetSpeed (long speed);
   int UpdateSpeed ();
   int GetTolerance(long& tolerance);
   int SetTolerance (long tolerance);

   // Pointer to COM Drive object
   TISCOPELib::IXDrive* pXDrive_;
   // Pointer to the global microscope object
   TISCOPELib::INikonTiPtr pMicroscope_;

   TISCOPELib::IScopeAccessory *TICOMDevice::GetTICOMDevice() {return pXDrive_;};
   TISCOPELib::IPositionAccessory* TICOMDrive::GetTICOMDrive() 
   {
      return pXDrive_;
   };
   TISCOPELib::INikonTiPtr GetMicroscope() {return pMicroscope_;};
  
protected:
   bool isMounted_;
   long lowerLimit_;
   long upperLimit_;

private:
   long speed_;
   long resolution_;
   std::vector<std::string> labels_;
};


class TICOMYDrive : public virtual TIYDrive, public virtual TICOMDrive 
{
public:
   int Initialize();

   int GetSpeed (long& speed) {UpdateSpeed(); speed = speed_; return 0;};
   int SetSpeed (long speed);
   int UpdateSpeed ();
   int GetTolerance(long& tolerance);
   int SetTolerance (long tolerance);

   // Pointer to COM Drive object
   TISCOPELib::IYDrive* pYDrive_;
   // Pointer to the global microscope object
   TISCOPELib::INikonTiPtr pMicroscope_;

   TISCOPELib::IScopeAccessory *TICOMDevice::GetTICOMDevice() {return pYDrive_;};
   TISCOPELib::IPositionAccessory* TICOMDrive::GetTICOMDrive() 
   {
      return pYDrive_;
   };
   TISCOPELib::INikonTiPtr GetMicroscope() {return pMicroscope_;};
  
protected:
   bool isMounted_;
   long lowerLimit_;
   long upperLimit_;

private:
   long speed_;
   long resolution_;
   std::vector<std::string> labels_;
};

class TICOMNosepiece : public TINosepiece, public TICOMPositionDevice
{
public:
   int Initialize();

   std::vector<std::string> GetLabels() {return labels_;};

   int UpdateObjectives();
   std::vector<TIObjective*> GetObjectives();

   // Pointer to COM Nosepiece object
   TISCOPELib::INosepiece *pNosepiece_;
   // Pointer to the global microscope object
   TISCOPELib::INikonTiPtr pMicroscope_;

   TISCOPELib::IScopeAccessory *TICOMDevice::GetTICOMDevice() {return pNosepiece_;};
   TISCOPELib::ITurretAccessory* TICOMPositionDevice::GetTICOMPositionDevice() {return pNosepiece_;};
   TISCOPELib::INikonTiPtr GetMicroscope() {return pMicroscope_;};

private:
   long type_; 
   std::string name_;
   std::string unit_;
   long value_;
   long resolution_;
   std::vector<std::string> labels_;
   std::vector<TICOMObjective*> objectives_;
};

 
class TICOMFilterBlockCassette : public TIFilterBlockCassette, public TICOMPositionDevice
{
public:
   int Initialize();

   std::vector<std::string> GetLabels() {return labels_;};
   int UpdateFilterBlocks();

   // Pointer to COM Nosepiece object
   TISCOPELib::IFilterBlockCassette *pFilterBlock_;
   // Pointer to the global microscope object
   TISCOPELib::INikonTiPtr pMicroscope_;

   TISCOPELib::IScopeAccessory *TICOMDevice::GetTICOMDevice() {return pFilterBlock_;};
   TISCOPELib::ITurretAccessory* TICOMPositionDevice::GetTICOMPositionDevice() {return pFilterBlock_;};
   TISCOPELib::INikonTiPtr GetMicroscope() {return pMicroscope_;};

protected:
   std::vector<TICOMFilterBlock*> filterBlocks_;
   //long lowerLimit_;
   //long upperLimit_;

private:
   //long type_; 
   //std::string name_;
   //std::string unit_;
   //long value_;
   //long resolution_;
   std::vector<std::string> labels_;
   //long position_;
   //long positionRangeLowerLimit_;
   //long positionRangeHigherLimit_;
};

class TICOMLightPath : public TILightPath, public TICOMPositionDevice
{
public:
   int Initialize();

   std::vector<std::string> GetLabels() {return labels_;};
   int UpdateLightPaths();

   // Pointer to COM LighPath object
   TISCOPELib::ILightPathDrive *pLightPath_;
   // Pointer to the global microscope object
   TISCOPELib::INikonTiPtr pMicroscope_;

   TISCOPELib::IScopeAccessory *TICOMDevice::GetTICOMDevice() {return pLightPath_;};
   TISCOPELib::ITurretAccessory* TICOMPositionDevice::GetTICOMPositionDevice() {return pLightPath_;};
   TISCOPELib::INikonTiPtr GetMicroscope() {return pMicroscope_;};

protected:
   //std::vector<TICOMFilterBlock*> filterBlocks_;
private:
   std::vector<std::string> labels_;
};

class TICOMModel : public TIModel
{
public:
   TICOMModel();
   ~TICOMModel();

   int Initialize();
   int Shutdown();
   bool IsInitialized() {return initialized_;};

   //TICOMPositionDevice nosepiece_;

private:
   // global pointer to the Microscope object
   TISCOPELib::INikonTiPtr pMicroscope_;
   TICOMNosepiece* nosepieceCOM_;
   TICOMFilterBlockCassette* filterBlock1COM_;
   TICOMFilterBlockCassette* filterBlock2COM_;
   TICOMZDrive* zDriveCOM_;
   TICOMXDrive* xDriveCOM_;
   TICOMYDrive* yDriveCOM_;
   TICOMPFSOffset* pfsOffsetCOM_;
   TICOMPFSStatus* pfsStatusCOM_;
   TICOMEpiShutter* pEpiShutterCOM_;
   TICOMDiaShutter* pDiaShutterCOM_;
   TICOMAuxShutter* pAuxShutterCOM_;
   TICOMLightPath* pLightPathCOM_;
   bool initialized_;
};




#endif