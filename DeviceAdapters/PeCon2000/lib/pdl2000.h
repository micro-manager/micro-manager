/*
 * PeCon Device Library 2000 Header File
 *
 * Copyright (c) 2013 PeCon GmbH\n
 * Ziegeleistrasse 50, 89155 Erbach, Germany\n
 * All rights reserved.
 *
 * $Date: 2013-10-24 00:43:12 +0200 (Do, 24 Okt 2013) $
 * $Revision: 54 $
 */

/** \defgroup PDL_API PDL2000 API
 * \{
 */

#ifndef __PDL2000_H__
#define __PDL2000_H__

#include <windows.h>

#define PDL2000_API WINAPI                      /**< PDL calling convention */

#if defined(PDL2000_LIBRARY)
#  define PDL2000_EXPORT __declspec(dllexport) PDL2000_API
#else
#  define PDL2000_EXPORT __declspec(dllimport) PDL2000_API
#endif

#define PDL2000_VERSION_MAJOR       1           /**< Major library version */
#define PDL2000_VERSION_MINOR       0           /**< Minor library version */
#define PDL2000_VERSION_REVISION    2           /**< Library revision code (patch level) */

/**
 * Helper macro to build version code.
 */
#define PDL2000_VERSION(major,minor,revision) \
    ((major) << 24 | (minor) << 16 | (revision))

/**
 * Library header file version code.
 * \see PdlGetVersion
 */
#define PDL2000_VERSION_CODE               \
    PDL2000_VERSION(PDL2000_VERSION_MAJOR, \
                    PDL2000_VERSION_MINOR, \
                    PDL2000_VERSION_REVISION)

/**
 * PDL Handle Type.
 */
typedef unsigned long PdlHandle;

/**
 * PDL Error Type.
 * \see PdlErrorEnum
 */
typedef unsigned long PdlError;

/**
 * Error Number Enumeration.
 * \see PdlError
 */
enum PdlErrorEnum
{
    PdlErrorSuccess                      = 0,        /**< Operation succeeded */
    PdlErrorFailed                       = 1,        /**< Function call failed */
    PdlErrorIllegalHandle                = 2,        /**< Illegal device handle*/
    PdlErrorIllegalParameter             = 3,        /**< Illegal Parameter */
    PdlErrorPointerMustNotBeNull         = 4,        /**< Pointer must not be null */
    PdlErrorTooManyDevices               = 5,        /**< Too many devices */
    PdlErrorSendFailed                   = 6,        /**< Sending data failed */
    PdlErrorRecvFailed                   = 7,        /**< Receiving data failed */
    PdlErrorNotSupported                 = 8,        /**< Operation not supported */
    PdlErrorFailure                      = 9,        /**< Operation failure detected */
    PdlErrorBufferTooSmall               = 10        /**< Buffer size too small */
};

/**
 * Device Status Enumeration.
 * \see PdlGetDeviceStatus
 */
enum PdlDeviceStatusEnum
{
    PdlDeviceStatusOk                    = 0x00,     /**< OK: No device error */
    PdlDeviceStatusBlOk                  = 0x80,     /**< Bootloader active: No error */
    PdlDeviceStatusBlRamTestError        = 0x81,     /**< Bootloader active: RAM test error */
    PdlDeviceStatusBlFlashChecksumError  = 0x82,     /**< Bootloader active: Flash checksum error - Reload Firmware */
    PdlDeviceStatusBlAppCrashed          = 0x83,     /**< Bootloader active: Application has crashed */
    PdlDeviceStatusBlEEPRomChecksumError = 0x84,     /**< Bootloader active: EEPROM checksum error */
    PdlDeviceStatusBlKeypadError         = 0x85,     /**< Bootloader active: Keypad error: key pressed */
};

/**
 * Control Mode Enumeration.
 * \see PdlGetControlMode, PdlSetControlMode
 */
enum PdlControlModeEnum
{
    PdlControlModeKeypadAndUsb           = 0x00,     /**< Keypad & USB (default) */
    PdlControlModeKeypadExclusive        = 0x01,     /**< Keypad exclusive */
    PdlControlModeUsbExclusive           = 0x02,     /**< USB exclusive */
};

/**
 * Channel Status Enumeration.
 * \see PdlGetChannelStatus
 */
enum PdlChannelStatusEnum
{
    PdlChannelStatusOk                   = 0x00,     /**< OK. No channel error */
    PdlChannelStatusNoSensor             = 0x01,     /**< NO SENSOR. No valid sensor signal detected */
    PdlChannelStatusOvertemp             = 0x02,     /**< OVERTEMP. Actual Temperature > Max Setpoint + 5°C */
    PdlChannelStatusOvercurrent          = 0x03,     /**< OVERCURRENT. Actual Current > 4A */
    PdlChannelStatusSumOvercurrent       = 0x04,     /**< SUM OVERCURRENT. Current CH1 + CH2 > 4.2A */ 
    PdlChannelStatusIdDetectError        = 0x05,     /**< ID DETECT ERROR. ID number mismatch after 5x ID request
                                                          over serial interface (3 identical ID's could not be found in the received 5 ID's) 
                                                          Auto-Detect will be repeated after a wait time, until 3 identical ID's are received. */ 
    PdlChannelStatusMainsVoltErr         = 0x06,     /**< MAINS VOLT ERR. Mains voltage on connected component is not in allowed range */
    PdlChannelStatusFanRotateErr         = 0x07,     /**< FAN ROTATE ERR. Fan(s) on connected component do not rotate */
    PdlChannelStatusOverheat             = 0x08,     /**< OVERHEAT. Overheat protection circuit on connected component has released */
    PdlChannelStatusMBTempTooHi          = 0x09,     /**< MB TEMP TOO HI. Mainboard temperature on connected component is too high */
    PdlChannelStatusChangeFilter         = 0x0A,     /**< CHANGE FILTER. Filters on/in connected component needs replacement */

    /*
     * gas controllers
     */
    PdlChannelStatusHeatingSensor        = 0x0B,     /**< HEATING SENSOR.
                                                          CO2: The sensor temperature is not +/- 5°C of the calibration temperature.
                                                          If 60 min. after device start/reset the sensor status bit is still active, the channel 
                                                          status changes to "FALSE SENS TEMP", because the sensor heater normally heats up the sensor
                                                          within 20-30 min. \n
                                                          (CO2-Sensor 2000: Status Bit 6 is set) \n
	                                                      O2: The sensor heating voltage is slowly increased and has not reached yet final value. */
    PdlChannelStatusWaitForSensor        = 0x0C,     /**< WAIT FOR SENSOR.
                                                          CO2: The sensor is not ready yet to give a value \n
                                                          (CO2-Sensor 2000: Status Bit 0 is set) */
    PdlChannelStatusAutoCompensate       = 0x0D,     /**< AUTO COMPENSATE.
                                                          CO2: The lamp intensity of the CO2-Sensor 2000 is automatically adjusted
                                                          to give a specified CO2-concentration (e.g. 100,00% with pure CO2) \n
                                                          (CO2-Sensor 2000: Status Bit 1 is set) */
    PdlChannelStatusFalseSensTemp        = 0x0E,     /**< FALSE SENS TEMP.
                                                          CO2: The sensor temperature is not +/- 6°C of the calibration temperature
                                                          after it was once inside the +/- 5°C tolerance band. Or the sensor status bit was still set
                                                          60min. after device start/reset. \n
                                                          (CO2-Sensor 2000: Status Bit 6 is set) */
    PdlChannelStatusLampOverLimit        = 0x0F,     /**< LAMP OVER LIMIT.
                                                          CO2: The lamp PWM limit of the connected sensor has been exceeded (<=450 / >=950). \n
                                                          (CO2-Sensor 2000: Status Bit 2 is set) */
    PdlChannelStatusNearADLimit          = 0x10,     /**< NEAR AD LIMIT.
                                                          CO2: One of the internal data points for CO2 measurement is near (or above)
                                                          the limit of the AD converter. CO2-concentratrion of this time cycle is therefore invalid.
                                                          (CO2-Sensor 2000: Status Bit 3 is set) */
    PdlChannelStatusFalseCalibVal        = 0x11,     /**< FALSE CALIB VAL.
                                                          CO2: The calibration values of the connected CO2-sensor 2000 head are 
                                                          invalid  (10%/100% value=0) -> Factory recalibration with 0/10/100% \n
                                                          (CO2-Sensor 2000: Status Bit 5 is set) */
    PdlChannelStatusNegativeValue        = 0x12,     /**< NEGATIVE VALUE.
                                                          CO2: The internal raw value is negative because of inverted detector signal.
                                                          (CO2-Sensor 2000: Status Bit 4 is set) */
    PdlChannelStatusHeatingVErr          = 0x14,     /**< HEATING V ERR.
                                                          O2: The heating voltage feedback control algorithm has reached the upper/lower
                                                          DA voltage output limit, while not have reached the set heating voltage read back by 
                                                          AD-measure­ment. */
    PdlChannelStatusReferenceGas         = 0x15,     /**< REFERENCE GAS.
                                                          CO2: The sensor chamber is flooded with CO2 reference gas. \n
                                                          O2: The sensor chamber is flooded with N2 reference gas. */
    PdlChannelStatusAmbientAir           = 0x16,     /**< AMBIENT AIR.
                                                          CO2: The sensor chamber is flooded with ambient air. \n
                                                          O2: The sensor chamber is flooded with ambient air. */
    PdlChannelStatusAdjOtherSens         = 0x17,     /**< ADJ OTHER SENS.
                                                          CO2: The O2 sensor is automatically adjusted. \n
                                                          O2: The CO2 sensor is automatically aging compensated (Auto Compensate). \n\n
                                                          This error is set to each sensor, that is within the same volume as the other sensor, 
                                                          whose value is currently corrected/calibrated. */
};

/**
 * Device Information Structure.
 *
 * This structure contains informations about the current enumerated device.
 * \note Be aware that the data behind the pointers are only valid until not \ref PdlEnumFree or
 * \ref PdlEnumReset is called. Do NEVER store copies of the pointers in your application. Instead use
 * strncpy() to get your private copy of the data.
 * \see PdlEnumNext
 */
struct PdlDeviceInfo
{
    const char* name;                           /**< Name of the device */
    const char* serial;                         /**< SerialNumber of the device */
    void* internal;                             /**< Pointer to internal data; do not use!  */
};

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Returns the library version code.
 *
 * This function is used to request the version number of the PDL2000 library.
 *
 * \param[out] version Pointer to version number (see \ref PDL2000_VERSION_CODE).
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorPointerMustNotBeNull <i>version</i> is NULL
 */
PdlError PDL2000_EXPORT PdlGetVersion(DWORD* version);

/**
 * Register for device notification events.
 *
 * This function registers a window for receiving device notifications events. This can
 * be used to get automatically informed about device arrival or removal. Any
 * application with a top-level window can receive notifications by processing
 * the <b>WM_DEVICECHANGE</b> message. The corresponding <i>wParam</i> is eighter <b>DBT_DEVICEARRIVAL</b>
 * or <b>DBT_DEVICEREMOVECOMPLETE</b>.
 *
 * \param windowHandle Handle to window which should receive the device notification events.
 * \param[out] notify Pointer to device notification handle.
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorIllegalParameter <i>windowHandle</i> is invalid
 * \retval PdlErrorPointerMustNotBeNull <i>notify</i> is NULL
 * \retval PdlErrorFailed Unable to register for device notification events.
 *                        To get extended error information, call <b>GetLastError</b>.
 */
PdlError PDL2000_EXPORT PdlRegisterNotification(HWND windowHandle, HDEVNOTIFY* notify);

/**
 * Unregister for device notification events.
 *
 * This function unregisters a window for receiving device notification events.
 *
 * \see PdlRegisterNotification
 * \param notify Device notification handle returned by \ref PdlRegisterNotification.
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorFailed Unable to unregister for device notification events.
 *                        To get extended error information, call <b>GetLastError</b>.
 */
PdlError PDL2000_EXPORT PdlUnregisterNotification(HDEVNOTIFY notify);

/** \{  */

/**
 * Resets the internal device enumeration.
 *
 * This function performs a complete device enumeration of all
 * available PeCon devices. Internally a list is allocated which holds
 * the device informations. Use \ref PdlEnumNext to query entry by entry.
 * Use \ref PdlEnumFree to free the internal allocated memory of the list.
 * 
 * \see PdlEnumFree, PdlEnumNext
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorFailed Device enumeration failed
 */
PdlError PDL2000_EXPORT PdlEnumReset(void);

/**
 * Returns an device information entry.
 *
 * This function returns an device information entry. Consecutive calls
 * will return entry by entry from the internal enumerated device list.
 * Make sure to call \ref PdlEnumReset prior this function. The returned
 * entry must be used to open a device connection by calling \ref PdlOpen.
 *
 * \see PdlEnumReset, PdlEnumFree
 * \param[out] devInfo Pointer to a device information structure.
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorPointerMustNotBeNull <i>devInfo</i> is NULL
 * \retval PdlErrorFailed No enumeration has been performed yet, call \ref PdlEnumReset.
 *                        Or, the end of the list has been reached.
 */
PdlError PDL2000_EXPORT PdlEnumNext(struct PdlDeviceInfo* devInfo);

/**
 * Frees the enumeration memory.
 *
 * This function frees the memory of the internal device information list.
 * It is not mandatory to free the memory at all. \ref PdlEnumReset will free the
 * old list before building the new one. The list is also freed when the
 * library gets unloaded. So it is the users choice when the list is freed.
 * \warning Keep in mind that all \ref PdlDeviceInfo pointers are invalid after
 * the list has been freed.
 *
 * \see PdlEnumReset, PdlEnumNext
 * \retval PdlErrorSuccess Success
 */
PdlError PDL2000_EXPORT PdlEnumFree(void);

enum PdlEnumDataField
{
    PdlEnumDataVID,
    PdlEnumDataPID,

    PdlEnumDataLast
};
PdlError PDL2000_EXPORT PdlEnumGetData(const struct PdlDeviceInfo* devInfo,
                                       PdlEnumDataField field,
                                       void* data,
                                       size_t dataSize);

/** \} */
/** \{ */

/**
 * Opens a connection to a device.
 *
 * This function opens a connection to a previously enumerated device.
 * A device <i>handle</i> is returned which has to be used together
 * with all other communication functions.
 *
 * \see PdlClose
 * \param[in] devInfo Pointer to device information returned by \ref PdlEnumNext.
 * \param[out] handle Pointer to handle
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorPointerMustNotBeNull <i>handle</i> or <i>devInfo</i> is NULL
 * \retval PdlErrorTooManyDevices Not enough space to manage the device
 * \retval PdlErrorFailed Device initialisation failed
 */
PdlError PDL2000_EXPORT PdlOpen(const struct PdlDeviceInfo* devInfo, PdlHandle* handle);

/**
 * Closes the device connection.
 *
 * This function closes a connection to the device specified by <i>handle</i>.
 *
 * \see PdlOpen
 * \param handle Handle returned by \ref PdlOpen
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorIllegalHandle <i>handle</i> is invalid
 */
PdlError PDL2000_EXPORT PdlClose(PdlHandle handle);

/** \} */
/** \{ */

/**
 * Reads the status of the device.
 *
 * This function must be called every 5 seconds to sustain
 * exclusive USB control (see \ref PdlControlModeUsbExclusive).
 *
 * \see PdlDeviceStatusEnum
 * \param handle Handle returned by \ref PdlOpen
 * \param[out] status Pointer to status value (see \ref PdlDeviceStatusEnum).
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorIllegalHandle <i>handle</i> is invalid
 * \retval PdlErrorSendFailed Data transmission failed
 * \retval PdlErrorRecvFailed Data reception failed
 * \retval PdlErrorFailure Command rejected by device
 * \retval PdlErrorPointerMustNotBeNull <i>status</i> is NULL
 */
PdlError PDL2000_EXPORT PdlGetDeviceStatus(PdlHandle handle, BYTE* status);

/**
 * Gets the Control Mode of the device.
 *
 * If Control Mode is \ref PdlControlModeUsbExclusive then \ref PdlGetDeviceStatus
 * must be called every 5 seconds to sustain this mode. Otherwise the device returns
 * to default Control Mode \ref PdlControlModeKeypadAndUsb.
 *
 * \see PdlControlModeEnum, PdlSetControlMode
 * \param handle Handle returned by \ref PdlOpen
 * \param[out] controlmode Pointer to controlmode value (see \ref PdlControlModeEnum).
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorIllegalHandle <i>handle</i> is invalid
 * \retval PdlErrorSendFailed Data transmission failed
 * \retval PdlErrorRecvFailed Data reception failed
 * \retval PdlErrorFailure Command rejected by device
 * \retval PdlErrorPointerMustNotBeNull <i>controlmode</i> is NULL
 */
PdlError PDL2000_EXPORT PdlGetControlMode(PdlHandle handle, BYTE* controlmode);

/**
 * Sets the Control Mode of the device.
 *
 * \see PdlControlModeEnum, PdlGetControlMode
 * \param handle Handle returned by \ref PdlOpen
 * \param[in,out] controlmode Pointer to controlmode value (see \ref PdlControlModeEnum).
 *                            The actual set controlmode is returned.
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorIllegalHandle <i>handle</i> is invalid
 * \retval PdlErrorSendFailed Data transmission failed
 * \retval PdlErrorRecvFailed Data reception failed
 * \retval PdlErrorFailure Command rejected by device
 * \retval PdlErrorPointerMustNotBeNull <i>controlmode</i> is NULL
 */
PdlError PDL2000_EXPORT PdlSetControlMode(PdlHandle handle, BYTE* controlmode);

/**
 * Sets the brightness of external LED illumination.
 *
 * \see PdlGetExternalIlluminationBrightness
 * \param handle Handle returned by \ref PdlOpen
 * \param[in,out] brightness Pointer to brightness value. 
 *                           The actual set brighness value is retuned.
 *                           -  0x00 ... no illumination
 *                           -  0x09 ... maximum illumination
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorIllegalHandle <i>handle</i> is invalid
 * \retval PdlErrorSendFailed Data transmission failed
 * \retval PdlErrorRecvFailed Data reception failed
 * \retval PdlErrorFailure Command rejected by device
 * \retval PdlErrorPointerMustNotBeNull <i>brightness</i> is NULL
 */
PdlError PDL2000_EXPORT PdlSetExternalIlluminationBrightness(PdlHandle handle, BYTE* brightness);

/**
 * Gets the brightness of external LED illumination.
 *
 * \see PdlSetExternalIlluminationBrightness
 * \param handle Handle returned by \ref PdlOpen
 * \param[out] brightness Pointer to brightness value.
 *                        -  0x00 ... no illumination
 *                        -  0x09 ... maximum illumination
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorIllegalHandle <i>handle</i> is invalid
 * \retval PdlErrorSendFailed Data transmission failed
 * \retval PdlErrorRecvFailed Data reception failed
 * \retval PdlErrorFailure Command rejected by device
 * \retval PdlErrorPointerMustNotBeNull <i>brightness</i> is NULL
 */
PdlError PDL2000_EXPORT PdlGetExternalIlluminationBrightness(PdlHandle handle, BYTE* brightness);

/** \} */
/** \{ */

/**
 * Gets the name for the component of channel.
 *
 * \param handle Handle returned by \ref PdlOpen
 * \param channel Channel number
 * \param[out] name Pointer to component name. The returned buffer contains max. 12 characters.
 * \param len Length of name buffer
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorIllegalHandle <i>handle</i> is invalid
 * \retval PdlErrorSendFailed Data transmission failed
 * \retval PdlErrorRecvFailed Data reception failed
 * \retval PdlErrorFailure Command rejected by device
 * \retval PdlErrorPointerMustNotBeNull <i>name</i> is NULL
 */
PdlError PDL2000_EXPORT PdlGetComponentName(PdlHandle handle, BYTE channel, char* name, size_t len);

/**
 * Gets the actual value of channel.
 *
 * \param handle Handle returned by \ref PdlOpen
 * \param channel Channel number
 * \param[out] value Pointer to value.
 *                   -  Step size 0.1°C
 *                   -  Min value 0.0°C (0x0000)
 *                   -  Max value 99.9°C (0x03E7)
 *                   -  0x1000 ... no sensor
 *                   -  0x1001 ... sensor error
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorIllegalHandle <i>handle</i> is invalid
 * \retval PdlErrorSendFailed Data transmission failed
 * \retval PdlErrorRecvFailed Data reception failed
 * \retval PdlErrorFailure Command rejected by device
 * \retval PdlErrorPointerMustNotBeNull <i>value</i> is NULL
 */
PdlError PDL2000_EXPORT PdlGetActualValue(PdlHandle handle, BYTE channel, SHORT* value);

/**
 * Sets the setpoint value of channel.
 *
 * \see PdlGetSetpointValue
 * \param handle Handle returned by \ref PdlOpen
 * \param channel Channel number
 * \param[in,out] value Pointer to setpoint value. 
 *                      The actual set setpoint-value is returned.
 *                      -  Step size 0.1°C
 *                      -  Min and max values are defined in parameter set
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorIllegalHandle <i>handle</i> is invalid
 * \retval PdlErrorSendFailed Data transmission failed
 * \retval PdlErrorRecvFailed Data reception failed
 * \retval PdlErrorFailure Command rejected by device
 * \retval PdlErrorPointerMustNotBeNull <i>value</i> is NULL
 */
PdlError PDL2000_EXPORT PdlSetSetpointValue(PdlHandle handle, BYTE channel, SHORT* value);

/**
 * Gets the setpoint value of channel.
 *
 * \see PdlSetSetpointValue
 * \param handle Handle returned by \ref PdlOpen
 * \param channel Channel number
 * \param[out] value Pointer to setpoint value.
 *                   -  Step size 0.1°C
 *                   -  Min and max values are defined in parameter set
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorIllegalHandle <i>handle</i> is invalid
 * \retval PdlErrorSendFailed Data transmission failed
 * \retval PdlErrorRecvFailed Data reception failed
 * \retval PdlErrorFailure Command rejected by device
 * \retval PdlErrorPointerMustNotBeNull <i>value</i> is NULL
 */
PdlError PDL2000_EXPORT PdlGetSetpointValue(PdlHandle handle, BYTE channel, SHORT* value);

/**
 * Gets the setpoint value range of channel.
 *
 * \see PdlSetSetpointValue
 * \param handle Handle returned by \ref PdlOpen
 * \param channel Channel number
 * \param[out] min Pointer to setpoint min value.
 *                   -  Step size 0.1°C
 *                   -  Min and max values are defined in parameter set
  * \param[out] max Pointer to setpoint max value.
 *                   -  Step size 0.1°C
 *                   -  Min and max values are defined in parameter set
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorIllegalHandle <i>handle</i> is invalid
 * \retval PdlErrorSendFailed Data transmission failed
 * \retval PdlErrorRecvFailed Data reception failed
 * \retval PdlErrorFailure Command rejected by device
 * \retval PdlErrorPointerMustNotBeNull <i>min</i> or <i>max</i> is NULL
 */
PdlError PDL2000_EXPORT PdlGetSetpointValueRange(PdlHandle handle, BYTE channel, SHORT* min, SHORT* max);

/**
 * Switches on or off the PID loop control for channel.
 *
 * The electrical current or the gas flow is shut-off in "off" state.
 *
 * \see PdlGetLoopControl
 * \param handle Handle returned by \ref PdlOpen
 * \param channel Channel number
 * \param[in,out] control Pointer to control value. 
 *                        The actual set control value is returned.
 *                        - 0 ... Control off
 *                        - 1 ... Control on
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorIllegalHandle <i>handle</i> is invalid
 * \retval PdlErrorSendFailed Data transmission failed
 * \retval PdlErrorRecvFailed Data reception failed
 * \retval PdlErrorFailure Command rejected by device
 * \retval PdlErrorPointerMustNotBeNull <i>control</i> is NULL
 */
PdlError PDL2000_EXPORT PdlSetLoopControl(PdlHandle handle, BYTE channel, BYTE* control);

/**
 * Gets the status of PID loop control for channel.
 *
 * \see PdlSetLoopControl
 * \param handle Handle returned by \ref PdlOpen
 * \param channel Channel number
 * \param[out] control Pointer to control value.
 *                     - 0 ... Control off
 *                     - 1 ... Control on
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorIllegalHandle <i>handle</i> is invalid
 * \retval PdlErrorSendFailed Data transmission failed
 * \retval PdlErrorRecvFailed Data reception failed
 * \retval PdlErrorFailure Command rejected by device
 * \retval PdlErrorPointerMustNotBeNull <i>control</i> is NULL
 */
PdlError PDL2000_EXPORT PdlGetLoopControl(PdlHandle handle, BYTE channel, BYTE* control);

/**
 * Reads the status of channel.
 *
 * \see PdlChannelStatusEnum
 * \param handle Handle returned by \ref PdlOpen
 * \param channel Channel number
 * \param[out] status Pointer to status value (see \ref PdlChannelStatusEnum).
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorIllegalHandle <i>handle</i> is invalid
 * \retval PdlErrorSendFailed Data transmission failed
 * \retval PdlErrorRecvFailed Data reception failed
 * \retval PdlErrorFailure Command rejected by device
 * \retval PdlErrorPointerMustNotBeNull <i>status</i> is NULL
 */
PdlError PDL2000_EXPORT PdlGetChannelStatus(PdlHandle handle, BYTE channel, BYTE* status);

/**
 * Activates the automatic calibration procedure for channel.
 *
 * \param handle Handle returned by \ref PdlOpen
 * \param channel Channel number
 * \param[out] value Pointer to sensor calibration value.
 * \retval PdlErrorSuccess Success
 * \retval PdlErrorIllegalHandle <i>handle</i> is invalid
 * \retval PdlErrorSendFailed Data transmission failed
 * \retval PdlErrorRecvFailed Data reception failed
 * \retval PdlErrorFailure Command rejected by device
 * \retval PdlErrorPointerMustNotBeNull <i>value</i> is NULL
 */
PdlError PDL2000_EXPORT PdlCalibrateSensor(PdlHandle handle, BYTE channel, SHORT* value);

/** \} */

#ifdef __cplusplus
}
#endif

/** \} */

#endif // __PDL2000_H__
