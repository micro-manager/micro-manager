 //////////////////////////////////////////////////////////////////////////////
// FILE:          TeensySLM.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Arduino board
//                Needs accompanying firmware to be installed on the board
// COPYRIGHT:     University of California, Berkeley, 2016
// LICENSE:       LGPL
//
// AUTHOR:        Henry Pinkard, hbp@berkeley.edu, 12/13/2016  
//
//

#ifndef _Arduino_H_
#define _Arduino_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_POSITION 101
#define ERR_INITIALIZE_FAILED 102
#define ERR_WRITE_FAILED 103
#define ERR_CLOSE_FAILED 104
#define ERR_BOARD_NOT_FOUND 105
#define ERR_PORT_OPEN_FAILED 106
#define ERR_COMMUNICATION 107
#define ERR_NO_PORT_SET 108
#define ERR_VERSION_MISMATCH 109


class CTeensySLM: public  CSLMBase<CTeensySLM>
{
public:
   CTeensySLM();
   ~CTeensySLM();
  
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
      int SetImage(unsigned int * pixels) {
		return DEVICE_UNSUPPORTED_COMMAND;	
	  }

      /**
       * Command the SLM to display the loaded image.
       */
      int DisplayImage();

      /**
       * Command the SLM to display one 8-bit intensity.
       */
      int SetPixelsTo(unsigned char intensity);

      /**
       * Command the SLM to display one 32-bit color.
       */
       int SetPixelsTo(unsigned char red, unsigned char green, unsigned char blue) {
	   		return DEVICE_UNSUPPORTED_COMMAND;	
	   }

      /**
       * Command the SLM to turn off after a specified interval.
       */
      int SetExposure(double interval_ms) {
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
      int IsSLMSequenceable(bool& isSequenceable) const {
	   		return DEVICE_UNSUPPORTED_COMMAND;	
	  }

      /**
       * Returns the maximum length of a sequence that the hardware can store.
       * @param nrEvents max length of sequence
       * @return errorcode (DEVICE_OK if no error)
       */
       int GetSLMSequenceMaxLength(long& nrEvents) {
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
       int AddToSLMSequence(const unsigned char * const pixels) {
		    		return DEVICE_UNSUPPORTED_COMMAND;	
	   }

      /**
       * Adds a new 32-bit (RGB) projection image to the sequence.
       * The image can either be added to a representation of the sequence in the 
       * adapter, or it can be directly written to the device
       * @param pixels An array of 32-bit RGB pixels whose length matches that expected by the SLM.
       * @return errorcode (DEVICE_OK if no error)
       */
       int AddToSLMSequence(const unsigned int * const pixels) {
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
			int OnShutterOpen(MM::PropertyBase* pPropt, MM::ActionType eAct);
		int OnManualVoltage(MM::PropertyBase* pPropt, MM::ActionType eAct);
		int OnRotation(MM::PropertyBase* pPropt, MM::ActionType eAct);
		int OnTilt(MM::PropertyBase* pPropt, MM::ActionType eAct);



private:
   
   static const unsigned char GLOBAL_HEADER[];
   static const unsigned char SHUTTER_HEADER[];
   static const unsigned char PATTERN_HEADER[];
   static const unsigned char COMMAND_SUCCESS[];

   bool initialized_;
   std::string name_;
   std::string port_;
   std::string patternString_;
   bool portAvailable_;
      MMThreadLock lock_;

		  long width_;
		  long height_;
		  unsigned char* pixels_;

		  long shutterOpen_;
	bool IsPortAvailable() {return portAvailable_;}
	int EnableShutter(bool open);
	long manualVoltage_;
	double rotation_;
	double tilt_;
	unsigned char lastModVal_;
	
   	 MMThreadLock& GetLock() {return lock_;}
	 int readCommandSuccess();
	 void ApplyModulationGradient();
	 int WriteImage(bool applyImmediately);



};

#endif //_Arduino_H_
