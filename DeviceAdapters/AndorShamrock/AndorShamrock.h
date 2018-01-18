  ///////////////////////////////////////////////////////////////////////////////
  // FILE:          AndorShamrock.h
  // PROJECT:       Micro-Manager
  // SUBSYSTEM:     DeviceAdapters
  //-----------------------------------------------------------------------------
  // DESCRIPTION:   Interface for the Andor Shamrock 
  //
  // COPYRIGHT:     University of California, San Francisco, 2009
  //
  // LICENSE:       This file is distributed under the "Lesser GPL" (LGPL) license.
  //                License text is included with the source distribution.
  //
  //                This file is distributed in the hope that it will be useful,
  //                but WITHOUT ANY WARRANTY; without even the implied warranty
  //                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  //
  //                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
  //                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
  //                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
  // AUTHOR:        Francis McCloy


  #ifndef _SHAMROCK_H_
  #define _SHAMROCK_H_

  #include "../../MMDevice/DeviceBase.h"
  #include "../../MMDevice/MMDevice.h"
  #include <stdint.h>
  #include <cstdio>
  #include <string>
  #include <vector>

  class AndorShamrock : public CDeviceBase<MM::Generic, AndorShamrock>
  {
  public:
     AndorShamrock();
     ~AndorShamrock();

    //Required by MM Device
    virtual int Initialize();

    virtual int Shutdown();
    void GetName(char* pszName) const;
    bool Busy();

    int OnSetWavelength(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetGrating(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetInputSideSlitWidth(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetInputDirectSlitWidth(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetOutputSideSlitWidth(MM::PropertyBase* pProp, MM::ActionType eAct);

    int SetSlitWidth(MM::PropertyBase* pProp, MM::ActionType eAct, int slit);

    int OnSetOutputDirectSlitWidth(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetFilter(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetPixelWidth(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetNumberOfPixels(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetShutter(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetInputPort(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetGratingOffset(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetDetectorOffset(MM::PropertyBase* pProp, MM::ActionType eAct);

    int setPort(MM::PropertyBase* pProp, MM::ActionType eAct, int flipper);

    int OnSetOutputPort(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetFocusMirror(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetDirectIrisPosition(MM::PropertyBase* pProp, MM::ActionType eAct);
    int OnSetSideIrisPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

    void SetGratingsProperty();
    void SetCoefficientsProperty();
    void SetWavelengthProperty();
	  void SetRayleighWavelengthProperty();
    void SetPixelWidthProperty();
    void SetNumberPixelsProperty();
    void SetFilterProperty();
    void SetSlitProperty();
    void SetShutterProperty();
    void SetFlipperProperty();
    void SetDetectorOffsetProperty();
    void SetGratingOffsetProperty();
    void SetFocusMirrorProperty();
	  int GetDetectorOffsetIndices(int *index1, int *index2);
    void SetDirectIrisPositionProperty();
    void SetSideIrisPositionProperty();

  private:
    std::vector<std::string> mvGratings;
    std::vector<std::string> mvFilters;
    std::vector<std::string> mvShutters;
  };

  #endif _SHAMROCK_H_
