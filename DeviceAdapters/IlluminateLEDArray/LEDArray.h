//////////////////////////////////////////////////////////////////////////////
// FILE:          LedArray.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for illuminate LED controller firmware
//                Needs accompanying firmware to be installed on the LED Array:
//                https://github.com/zfphil/illuminate
//
// COPYRIGHT:     Regents of the University of California
// LICENSE:       LGPL
//
// AUTHOR:        Henry Pinkard, hbp@berkeley.edu, 12/13/2016
// AUTHOR:        Zack Phillips, zkphil@berkeley.edu, 3/1/2019
//
//////////////////////////////////////////////////////////////////////////////


#ifndef _ILLUMINATE_H_
#define _ILLUMINATE_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//////////////////////////////////////////////////////////////////////////////
#define ERR_UNKNOWN_POSITION 101
#define ERR_INITIALIZE_FAILED 102
#define ERR_WRITE_FAILED 103
#define ERR_CLOSE_FAILED 104
#define ERR_BOARD_NOT_FOUND 105
#define ERR_PORT_OPEN_FAILED 106
#define ERR_COMMUNICATION 107
#define ERR_NO_PORT_SET 108
#define ERR_VERSION_MISMATCH 109
#define COMMAND_TERMINATOR '\n'
#define SERIAL_DELAY_MS 30

const char* g_Keyword_DeviceName = "Illuminate-Led-Array";
//const char* g_Keyword_DeviceNameVirtualShutter = "Illuminate-Led-Array-Virtual-Shutter";

const char * g_Keyword_ColorBalanceRed = "ColorBalanceRed";      // Global intensity with a maximum of 255
const char * g_Keyword_ColorBalanceGreen = "ColorBalanceGreen";  // Global intensity with a maximum of 255
const char * g_Keyword_ColorBalanceBlue = "ColorBalanceBlue";    // Global intensity with a maximum of 255
const char * g_Keyword_Brightness = "Brightness";
const char * g_Keyword_NumericalAperture = "NumericalAperture"; // Setting the numerical aperture
const char * g_Keyword_SetArrayDistanceMM = "ArrayDistanceFromSample";
const char * g_Keyword_Pattern = "IlluminationPattern";
const char * g_Keyword_PatternOrientation = "IlluminationPatternOrientation";
const char * g_Keyword_AnnulusWidth = "AnnulusWidthNa";
const char * g_Keyword_LedList = "ManualLedList";
const char * g_Keyword_Reset = "Reset";
const char * g_Keyword_Shutter = "ShutterOpen";

// Device Parameters
const char * g_Keyword_WavelengthRed = "ColorWavelengthRedMicrons";
const char * g_Keyword_WavelengthGreen = "ColorWavelengthGreenMicrons";
const char * g_Keyword_WavelengthBlue = "ColorWavelengthBlueMicrons";
const char * g_Keyword_LedCount = "LedCount";
const char * g_Keyword_PartNumber = "PartNumber";
const char * g_Keyword_SerialNumber = "SerialNumber";
const char * g_Keyword_MacAddress = "MacAddress";
const char * g_Keyword_TriggerInputCount = "TriggerInputCount";
const char * g_Keyword_TriggerOutputCount = "TriggerOutputCount";
const char * g_Keyword_NativeBitDepth = "NativeBitDepth";
const char * g_Keyword_LedArrayType = "Type";
const char * g_Keyword_InterfaceVersion = "InterfaceVersion";
const char * g_Keyword_ColorChannelCount = "ColorChannelCount";
const char * g_Keyword_LedPositions = "LedPositionsCartesian";

// Low-Level Serial IO
const char * g_Keyword_Response = "SerialResponse";
const char * g_Keyword_Command = "SerialCommand";

// LED pattern labels
const char * g_Pattern_None = "None";
const char * g_Pattern_Brightfield = "Brightfield";
const char * g_Pattern_Darkfield = "Darkfield";
const char * g_Pattern_Dpc = "DPC";
const char * g_Pattern_ColorDpc = "Color DPC";
const char * g_Pattern_ColorDarkfield = "Color Darkfield";
const char * g_Pattern_ManualLedIndices = "Manual LED Indicies";
const char * g_Pattern_Annulus = "Annulus";
const char * g_Pattern_HalfAnnulus = "Half Annulus";
const char * g_Pattern_CenterLed = "Center LED";
const char * g_Pattern_Clear = "Clear";

// LED Pattern orientation labels
const char * g_Orientation_Top = "Top";
const char * g_Orientation_Bottom = "Bottom";
const char * g_Orientation_Left = "Left";
const char * g_Orientation_Right = "Right";

class LedArray: public  CSLMBase<LedArray>
{
public:
   LedArray();
   ~LedArray();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();

   bool Busy();
   void GetName(char *) const;

      // SLM API
      /**
       * Load the image into the SLM device adapter.
       */
      int SetImage(unsigned char * pixels);

      /**
      * Load a 32-bit image into the SLM device adapter.
      */
      int SetImage(unsigned int *) {
		return DEVICE_UNSUPPORTED_COMMAND;	
	  }

      /**
       * Command the SLM to display the loaded image.
       */
      int DisplayImage();

      /**
       * Command the SLM to display one 8-bit intensity.
       */
      int SetPixelsTo(unsigned char);

      /**
       * Command the SLM to display one 32-bit color.
       */
       int SetPixelsTo(unsigned char, unsigned char, unsigned char) {
	   		return DEVICE_UNSUPPORTED_COMMAND;	
	   }

      /**
       * Command the SLM to turn off after a specified interval.
       */
      int SetExposure(double) {
		  		return DEVICE_UNSUPPORTED_COMMAND;	
	  }

      /**
       * Find out the exposure interval of an SLM.
       */
       double GetExposure() {
	 		return DEVICE_UNSUPPORTED_COMMAND;	
	   }

      /**
       * Get the SLM width in pixels.
       */
       unsigned GetWidth() {
		return width_;
	   }

      /**
       * Get the SLM height in pixels.
       */
      virtual unsigned GetHeight() {
		return height_;
	  }

      /**
       * Get the SLM number of components (colors).
       */
      unsigned GetNumberOfComponents() {
		return 1;
	  }

      /**
       * Get the SLM number of bytes per pixel.
       */
      unsigned GetBytesPerPixel() {
		return 1;
	  }

      // SLM Sequence functions
      // Sequences can be used for fast acquisitions, synchronized by TTLs rather than
      // computer commands. 
      // Sequences of images can be uploaded to the SLM.  The SLM will cycle through
      // the uploaded list of images (perhaps triggered by an external trigger or by
      // an internal clock.
      // If the device is capable (and ready) to do so IsSLMSequenceable will return
      // be true. If your device can not execute sequences, IsSLMSequenceable returns false.

      /**
       * Lets the core know whether or not this SLM device accepts sequences
       * If the device is sequenceable, it is usually best to add a property through which 
       * the user can set "isSequenceable", since only the user knows whether the device
       * is actually connected to a trigger source.
       * If IsSLMSequenceable returns true, the device adapter must also implement the
       * sequencing functions for the SLM.
       * @param isSequenceable signals whether other sequence functions will work
       * @return errorcode (DEVICE_OK if no error)
       */

      int IsSLMSequenceable(bool&) const {
	   		return DEVICE_UNSUPPORTED_COMMAND;	
	  }

      /**
       * Returns the maximum length of a sequence that the hardware can store.
       * @param nrEvents max length of sequence
       * @return errorcode (DEVICE_OK if no error)
       */
       int GetSLMSequenceMaxLength(long&) {
		   		return DEVICE_UNSUPPORTED_COMMAND;	
	  }

      /**
       * Tells the device to start running a sequnece (i.e. start switching between images 
       * sent previously, triggered by a TTL or internal clock).
       * @return errorcode (DEVICE_OK if no error)
       */
       int StartSLMSequence() {
		   		return DEVICE_UNSUPPORTED_COMMAND;	
	  }

      /**
       * Tells the device to stop running the sequence.
       * @return errorcode (DEVICE_OK if no error)
       */
       int StopSLMSequence() {
		   		return DEVICE_UNSUPPORTED_COMMAND;	
	  }

      /**
       * Clears the SLM sequence from the device and the adapter.
       * If this function is not called in between running 
       * two sequences, it is expected that the same sequence will run twice.
       * To upload a new sequence, first call this function, then call
       * AddToSLMSequence(image)
       * as often as needed.
       * @return errorcode (DEVICE_OK if no error)
       */
       int ClearSLMSequence() {
		   		return DEVICE_UNSUPPORTED_COMMAND;	
	  }

      /**
       * Adds a new 8-bit projection image to the sequence.
       * The image can either be added to a representation of the sequence in the 
       * adapter, or it can be directly written to the device
       * @param pixels An array of 8-bit pixels whose length matches that expected by the SLM.
       * @return errorcode (DEVICE_OK if no error)
       */
       int AddToSLMSequence(const unsigned char * const) {
		    		return DEVICE_UNSUPPORTED_COMMAND;	
	   }

      /**
       * Adds a new 32-bit (RGB) projection image to the sequence.
       * The image can either be added to a representation of the sequence in the 
       * adapter, or it can be directly written to the device
       * @param pixels An array of 32-bit RGB pixels whose length matches that expected by the SLM.
       * @return errorcode (DEVICE_OK if no error)
       */
       int AddToSLMSequence(const unsigned int * const) {
	    		return DEVICE_UNSUPPORTED_COMMAND;	
	   }

      /**
       * Sends the complete sequence to the device.
       * If the individual images were already send to the device, there is 
       * nothing to be done.
       * @return errorcode (DEVICE_OK if no error)
       */
       int SendSLMSequence() {
		    		return DEVICE_UNSUPPORTED_COMMAND;	
	   }



   // action interface
   // ----------------
      int OnPort(MM::PropertyBase* pPropt, MM::ActionType eAct);
	  int OnWidth(MM::PropertyBase* pPropt, MM::ActionType eAct);
	  int OnHeight(MM::PropertyBase* pPropt, MM::ActionType eAct);
	  int OnPattern(MM::PropertyBase* pPropt, MM::ActionType eAct);
      int OnColorBalanceRed( MM::PropertyBase* pPropt, MM::ActionType eAct);
	  int OnColorBalanceGreen(MM::PropertyBase* pPropt, MM::ActionType eAct);
	  int OnColorBalanceBlue(MM::PropertyBase* pPropt, MM::ActionType eAct);
      int OnShutterOpen(MM::PropertyBase* pPropt, MM::ActionType eAct);
	  int OnAperture(MM::PropertyBase* pPropt, MM::ActionType eAct);	 
	  int OnDistance(MM::PropertyBase* pPropt, MM::ActionType eAct);
	  int OnPatternOrientation(MM::PropertyBase* pPropt, MM::ActionType eAct);
	  int OnAnnulusWidth(MM::PropertyBase* pProp, MM::ActionType pAct);
	  int OnSetManualLedList(MM::PropertyBase* pPropt, MM::ActionType eAct);
	  int OnReset(MM::PropertyBase* pPropt, MM::ActionType eAct);
	  int OnCommand(MM::PropertyBase* pPropt, MM::ActionType eAct);
	  int OnBrightness(MM::PropertyBase* pPropt, MM::ActionType eAct);

private:
   
   bool initialized_;
   std::string name_;
   std::string port_;
   std::string patternString_;
   bool portAvailable_;
      MMThreadLock lock_;
		  long width_;
		  long height_;
		  unsigned char* pixels_;
	bool IsPortAvailable() {return portAvailable_;}

	long shutterOpen_, bf_, df_;
	long lsingle_; // LED index
	std::string lmult_;

	std::string _pattern;
	std::string _pattern_orientation;
	std::string _led_indicies;
	std::string _command;
	std::string _serial_answer;
	double numerical_aperture;
	double annulus_width;
	double array_distance_z;
	double interface_version;
	long led_count;
	int color_channel_count, bit_depth;
	int trigger_input_count, trigger_output_count;
	int part_number, serial_number;
	long color_r, color_g, color_b, brightness;
	double * * led_positions_cartesian;
	bool array_is_color;

	// Action functions with LEDs:
    int UpdateColor(long redint, long greenint, long blueint);
	int SetBrightness(long brightness);
	int DrawLedList(const char * led_list_char);
	int DrawDpc(std::string type);
	int DrawAnnulus(double minna, double maxna);
	int DrawHalfAnnulus(std::string type, double minna, double maxna);
	int SetNumericalAperture(double numa);
	int SetArrayDistance(double dist);
	int Clear();
	int UpdatePattern();
	int Reset();
	int ReadResponse();
	int GetDeviceParameters();
	int SetMachineMode(bool mode);
	int SendCommand(const char * command, bool get_response);
	int GetResponse();
	int SyncState();
	int ReadLedPositions();

	unsigned char lastModVal_;
	
   	 MMThreadLock& GetLock() {return lock_;}



};


/*
class LedArrayVirtualShutter : public CShutterBase<LedArrayVirtualShutter>
{
public:
   LedArrayVirtualShutter();
   ~LedArrayVirtualShutter();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown() {initialized_ = false; return DEVICE_OK;}
  
   void GetName(char* pszName) const;
   bool Busy(){return false;}

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire (double) { return DEVICE_UNSUPPORTED_COMMAND;}

private:
   std::vector<std::string> availableDAs_;
   std::string DADeviceName1_;
   std::string DADeviceName2_;
   MM::SignalIO* DADevice1_;
   bool initialized_;
};
*/

#endif 
