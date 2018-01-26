#ifndef _CONSTANTS_H_
#define _CONSTANTS_H_

const int NUM_PORTS = 2;
const int NUM_DETECTORS = 4;
const int NUM_DETECTORS_PER_PORT = 2;
const int NUM_FLIPPER_POS = 2;
const int PORT_1 = 1;
const int PORT_2 = 2;
const int MAX_NUM_SLITS = 4;
const float MAX_SLIT_WIDTH = 2500;
const float MIN_SLIT_WIDTH = 10;

extern const char* g_SpectrographName;
extern const char* g_DeviceDescription;
extern const char* gsz_SerialNo;
extern const char* gsz_CentreWavelength;
extern const char* gsz_RayleighWavelength;
extern const char* gsz_Grating;
extern const char* gsz_Filter;
extern const char* gsz_Shutter;
extern const char* gsz_PixelWidth;
extern const char* gsz_NoPixels;
extern const char* gsz_gratingoffset;
extern const char* gsz_detectoroffset;
extern const char* gsz_Coefficients;
extern const char* gsz_SlitWidth[4];
extern const char* gsz_Port[2];
//const char* gsz_flipperMirror[2] = {"DIRECT","SIDE"};
extern const char* gsz_FocusMirror;
extern const char* gsz_DirectIrisPosition;
extern const char* gsz_SideIrisPosition;


#endif _CONSTANTS_H_