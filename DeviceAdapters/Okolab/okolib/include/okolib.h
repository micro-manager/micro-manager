/**
 * @file okolib.h
 * @mainpage Introduction
 * @tableofcontents
 *
 * This documentation describes the SDK specification used to operate devices
 * manufactured by <a href="http://www.oko-lab.com/" target="_blank">Okolab</a>.
 * The SDK will include an example C/C++ file showing typical usage and a description
 * of the API interface with the Okolab devices.
 *
 * @section intr1 Files
 *
 * The SDK consists of the following files:
 * 		- Headers:  okolib.h
 * 		- Binaries: okolib.dll, okolib.lib, libserialport.dll
 * 		- Resources: okolib.db
 *
 * To access a device, the calling applications has to call functions of okolib.dll. Some compilers requires a lib file (okolib.lib) to build.
 * The interface to the library is described by okolib.h.
 *
 * Some files are needed for development purposes only. The calling built application needs:
 * 		- okolib.dll and libserialport.dll: they must reside in the same directory of the calling application
 * 		- okolib.db: it is a db configuration file and can be moved in a different path (see @ref oko_LibInit)
 *
 * @section intr2 Concepts
 *
 * @subsection intr21 Module
 * A module is a main controlled physical quantity (Temperature, CO2, O2),
 * with a name, a dimension unit, a current measured value, a setpoint and other features.
 * Modules are a basic way to use the library, the usage is very simple but a little bit limited.
 *
 * @subsection intr22 Device
 * A device is strictly related to a communication port and it is made up
 * by one or more physical controller units (e.g. Bold Line stacked units).
 * A device can be manually (@ref oko_DeviceOpen) or automatically (@ref oko_DevicesDetect) opened.
 *
 * @subsection intr23 Property
 * Each device has several properties.
 * Each property has some information (@ref PropInfo) and can be read or write.
 *
 * @section intr3 Usage
 * The operations that are supported are:
 *
 * - @ref Library. Prepare the library to be used and queried
 * - @ref Modules. Allow to detect the available modules and use them.
 * - Devices and Properties.
 * 		- @ref Open (prepare a device to be used)
 * 		- @ref Device (query status of the device)
 * 		- @ref PropInfo (for the selected device)
 * 		- @ref PropAuto
 * 		- @ref PropRead
 * 		- @ref PropWrite
 * 		- @ref Commands
 *
 * See also:
 * @ref usage1
 *
 * @section intr4 Information
 * @author Marco Di Pasqua <dipasqua@oko-lab.com>
 * @author Giulio Pasquariello <pasquariello@oko-lab.com>
 * @author Domenico Mastronardi
 * @copyright Okolab S.r.l. <a href="http://www.oko-lab.com/" target="_blank">oko-lab.com</a> <software.support@oko-lab.com>
 * @copyright This software uses code of <a href="http://sigrok.org/wiki/Libserialport" target="_blank">libserialport</a>
 * licensed under the <a href="https://www.gnu.org/licenses/lgpl-3.0.html" target="_blank">GNU LGPL, version 3</a>
 * and its source can be downloaded <a href="http://sigrok.org/download/source/libserialport/libserialport-0.1.0.tar.gz">here</a>
 *
 */

/** @page changelog Changelog
 *
 * @tableofcontents
 *
 * @section v130 1.3.0
 * IMPROVED Increased maximum buffer
 *
 * @section v121 1.2.1
 * FIXED Error code when operation cannot be executed by the device
 *
 * @section v120 1.2.0
 * FIXED Search for sub-products was not performed at all
 *
 * @section v111 1.1.1
 * FIXED busy error in @ref oko_DeviceOpen
 *
 * @section v101 1.0.1
 * FIXED @ref oko_LibGetPortName now uses ports cached by @ref oko_LibGetNumberOfPorts
 *
 * @section v100 1.0.0
 * ADDED @ref oko_DevicesDetectByName
 *
 * @section v094 0.9.4
 * ADDED @ref oko_DeviceDetectSingleByName
 *
 * @section v093 0.9.3
 * - Used new version (0.1.1) of libserialport that allows usb detection
 * ADDED @ref oko_LibSetSuggestedUSBOnly
 *
 * @section v092 0.9.2
 * - FIXED DEMO mode bug, modifying okolib.demo template
 *
 * @section v091 0.9.1
 * - FIXED possible memory access error in @ref oko_LibGetLastError
 *
 * @section v090 0.9.0
 * - Improved internal check to avoid false positive communication errors
 *
 * @section v080 0.8.0
 * - FIXED auto-connection after OKO_ERR_PORT_NOTVALID error (removed USB device)
 * - ADDED @ref oko_PropertyGetWriteOnly
 * - ADDED functions to write a parameter in volatile memory
 * - ADDED @ref oko_PropertyGetWriteType
 *
 * @section v072 0.7.2
 * - FIXED @ref OKO_ERR_TIMEOUT was returned instead of @ref OKO_ERR_NOTSUPP (when checksum was used)
 *
 * @section v070 0.7.0
 * - Improved asynchronous write logic: now it uses a queue and it's thread-safe
 * - Fixed auto-update value hang after a while
 * - Restored previous serial settings
 *
 * @section v060 0.6.0
 * - Added file info
 * - Improved serial settings
 *
 * @section v057 0.5.7
 * - FIXED random @ref OKO_ERR_PORT_NOTVALID errors
 *
 * @section v056 0.5.6
 * - FIXED Checksum detection for some stand-alone gas controllers (e.g. CO2-UNIT-3L)
 * - FIXED Checksum protocol error with special characters
 *
 * @section v055 0.5.5
 * - ADDED "Disabled" value for "status" parameters
 *
 * @section v054 0.5.4
 * - FIXED @ref OKO_ERR_PORT_NOTVALID
 * - FIXED Connection is restored if USB device is unplugged and then plugged
 *
 * @section v053 0.5.3
 * - ADDED @ref OKO_ERR_COMM and @ref OKO_ERR_TIMEOUT errors
 *
 * @section v052 0.5.2
 * - IMPROVED error checking
 * 
 * @section v046 0.4.6
 * - ADDED @ref oko_LibGetPortName
 * - FIXED @ref oko_DeviceClose, now device port is reset
 *
 * @section v045 0.4.5
 * - ADDED data playback and logging.
 * - FIXED @ref oko_ModulesDetect now returns OKO_OK even if a single module is found.
 * - FIXED @ref oko_LibInit now is able to expand a complex path.
 *
 * @section v044 0.4.4
 * - FIXED Temperature write setpoint with integer values (eg. 37.0).
 * - ADDED default limits for devices without minimum and maximum commands (eg. UNO)
 *
 * @section v043 0.4.3
 * - FIXED Temperature module detection using Smart Box.
 *
 * @section v042 0.4.2
 * - FIXED strange characters returned instead of degree symbol (&deg;)
 *
 * @section v041 0.4.1
 * - MODIFIED modules enable/disable logic, see @ref oko_ModuleGetEnabled and @ref oko_ModuleSetEnabled
 *
 * @section v040 0.4.0
 * - ADDED Checksum protocol
 *
 * @section v031 0.3.1
 * - FIXED oko_ModuleGetDetails: can_disable was TRUE, even for firmware version without this property.
 *
 * @section v030 0.3.0
 * - ADDED Modules component (see @ref Modules)
 * - FIXED crash when USB is connected to a Slave device
 *
 * @section v023 0.2.3
 * - ADDED Commands component (see @ref Commands)
 *
 * @section v022 0.2.2
 * - MANAGED new database version
 * - ADDED subproducts management
 * - MODIFIED @ref oko_PropertyReadString. Now it returns the enumeration name if the property type is enumeration
 *
 * @section v021 0.2.1
 * - MANAGED new database version
 * - FIXED some parameters
 *
 * @section v020 0.2.0
 * - ADDED error messages functions: @ref oko_LibGetLastError and @ref oko_DeviceGetLastError
 * - MODIFIED @ref oko_LibInit function: now the database file path can be selected
 *
 * @section v011 0.1.1
 * - IMPROVED communication timeouts
 *
 * @section v010 0.1.0
 * - First release
 *
 *
 **/

/** @page usage1 Typical Implementation
 *
 * @tableofcontents
 *
 * A typical implementation of the communication with an
 * Okolab device using the SDK would consist of a sequence
 * of function calls depending on the overall scenario.
 *
 * @section step1 Modules
 * @subsection case11 Case 1
 * Detect all the avaiable modules and collect their fixed details.
 * - @ref oko_ModulesDetect  Obtain the number of modules connected and their type as an array. For each module type
 *   + @ref oko_ModuleGetDetails
 *
 * @subsection case12 Case 2
 * Using the already obtained types, periodically query setpoint and current value of each module.
 * - @ref oko_ModuleGetCurrentValue
 * - @ref oko_ModuleGetSetpointValue
 *
 * @subsection case13 Case 3
 * Using the already obtained types, change the setpoint of each module.
 * - @ref oko_ModuleSetSetpointValue
 *
 * @section step2 Device initialization
 * @subsection case21 Case 1
 * No previous knowledge of the connected device
 * - @ref oko_DevicesDetect  Obtain the number of device connected and their handles as an array. For each handle
 *   + @ref oko_DeviceGetPortName  Store the port name to avoid going through the detect procedure next time software is started
 *
 * @subsection case22 Case 2
 * Previous knowledge of the device \e ComPort# (e.g. by user input or previously stored by the application).
 * - @ref oko_DeviceOpen  Obtain the handle of the device connected to \e ComPort#.
 *
 *
 * @section step3 Properties initialization
 *   + @ref oko_PropertiesGetNumber  Get the total number of properties for the current device
 *   + at this point the application should iterate calling @ref oko_PropertyGetName to create an array of \e PropertyNames. For each \e PropertyName
 *     + @ref oko_PropertyIsMain can be used to store main features only
 *     + @ref oko_PropertyGetType the @ref oko_prop_type of this property will be used to call the appropriate read/write function must be called
 *     + @ref oko_PropertyGetReadOnly when this returns false the property can also be write, if it can be write
 *       + @ref oko_PropertyHasLimits verify if the min and max of the property are available, if so
 *         + @ref oko_PropertyGetLimits read the min and max of the property from the device
 *     + @ref oko_PropertyGetDescription  optional, used to obtain property additional info
 *     + @ref oko_PropertyGetUnit  optional, used to obtain property additional info
 *
 *
 * @section step4 Property usage by Name
 * @subsection case41 Case 1
 * Get the last value of a read only property
 *   + @ref oko_PropertyGetType check the property type (e.g. @ref OKO_PROP_DOUBLE)
 *   + call the appropriate function @ref oko_PropertyReadDouble
 *
 * @subsection case42 Case 2
 * First update the value from the device and then read a property value of a known type
 *   + @ref oko_PropertyUpdate
 *   + @ref oko_PropertyReadDouble
 *
 * @subsection case43 Case 3
 * Synchronously change the value of a property (e.g. with double type)
 *   + @ref oko_PropertyWriteDouble with async = false
 *
 * @subsection case44 Case 4
 * Change the value of a property (e.g. with double type), without waiting for the result
 *   + @ref oko_PropertyWriteDouble with async = true, to avoid delay in the calling application execution
 *
 * @section step5 Auto-updating a property
 * A property can be auto-updated by an internal thread, so the application
 * doesn't need to esplicity update it, calling @ref oko_PropertyUpdate.
 *
 * @subsection case51 Case 1
 * Make a property auto-updating
 *   + @ref oko_PropertyAutoUpdate
 *
 * @subsection case52 Case 2
 * Read the last property value
 *   + @ref oko_PropertyReadDouble
 *
 *
 * @section step6 Close Device
 * @subsection case61 Case 1
 * No previous knowledge of the connected device
 * - @ref oko_DevicesCloseAll    Close the handles to all detected devices.
 *
 * @subsection case62 Case 2
 * Previous knowledge of the device
 * - @ref oko_DeviceClose close the device related to the specified handle.
 **/



/** @page demo_mode Demo device
 *
 * @tableofcontents
 *
 * To test Okolib calls without any physical devices
 * a demo device can be used.
 * The COM Port descriptor of this demo device is "DEMO".
 * To create a demo device, a special okolib.demo file needs to be saved
 * in the same folder as okolib.db file.
 * Contact Okolab to obtain the okolib.demo file that suits your requirements.
 *
 **/

#ifndef _OKOLIB_H_
#define _OKOLIB_H_

#ifdef _MSC_VER
    #if _MSC_VER >= 1600
        #include <stdint.h>
    #else
        typedef __int8              int8_t;
        typedef __int16             int16_t;
        typedef __int32             int32_t;
        typedef __int64             int64_t;
        typedef unsigned __int8     uint8_t;
        typedef unsigned __int16    uint16_t;
        typedef unsigned __int32    uint32_t;
        typedef unsigned __int64    uint64_t;
    #endif
#elif __GNUC__ >= 3
	#include <stdint.h>
#endif




#ifdef __cplusplus
extern "C" {
#endif

#ifdef _WIN32

#if defined(OKO_NO_DLL)             /* Static import */
#  define OKO_API
#elif defined(OKO_BUILDING_DLL)     /* DLL export    */
#  define OKO_API __declspec (dllexport)
#else                               /* DLL import    */
#  define OKO_API __declspec (dllimport)
#endif /* Not BUILDING_DLL */

/* Define calling convention in one place, for convenience. */
#define OKO_EXPORT __cdecl

/* Microsoft VBA */
#ifdef OKO_VBA
#undef OKO_EXPORT
#define OKO_EXPORT __stdcall
#endif

#else /* _WIN32 not defined. */

/* Define with no value on non-Windows OSes. */
#define OKO_API
#define OKO_EXPORT

#endif

/**
@defgroup Errors Error codes
@{
*/

/** Specifies the return values */
typedef enum _oko_res_type
{
	OKO_OK 					=  0,	//!< Operation completed successfully.
	OKO_ERR_UNINIT 			= -1,	//!< Library not initialized yet.
	OKO_ERR_ARG 			= -2,	//!< Invalid arguments were passed to the function.
	OKO_ERR_FAIL 			= -3,	//!< A system error occurred while executing the operation.
	OKO_ERR_NOTSUPP 		= -4,	//!< The requested operation is not supported by this system or device.
	OKO_ERR_CLOSED 			= -5,	//!< The specified device is not opened.
	OKO_ERR_UNCONN 			= -6,	//!< The specified device is not connected.
	OKO_ERR_PORT_BUSY  		= -7,   //!< Serial port busy
	OKO_ERR_PORT_CFG  		= -8, 	//!< Port configuration failed
	OKO_ERR_PORT_NOTVALID	= -9, 	//!< Port not valid
	OKO_ERR_DB_OPEN  		= -10, 	//!< Database error on open
	OKO_ERR_PROP_NOTFOUND	= -11, 	//!< Property not found
	OKO_ERR_DEV_NOTFOUND	= -12,  //!< Device not found
	OKO_ERR_COMM	   		= -13,  //!< Communication error
	OKO_ERR_ENUM_NOTFOUND  	= -14,  //!< Enum of the Property not found
	OKO_ERR_MODULE_NOTFOUND	= -15,  //!< Module specified not found
	OKO_ERR_DEV_SLAVE		= -16,  //!< Slave device
	OKO_ERR_DEV_NOTRUNNING	= -17,  //!< Device not running
	OKO_ERR_MEMORY			= -18,  //!< Memory allocation failed
	OKO_ERR_TIMEOUT			= -19,  //!< Timeout error
	OKO_ERR_UNDEF  			= -999, //!< Undefined error
} oko_res_type;

/**
 * @}
 * @defgroup Library Library access functions
 * @{
*/

/*!
 * Initialize the library
 * @param[in] db_path The path where okolib.db file is installed.
 * The specified path can be absolute or relative to the current working directory.
 * If it is null or an empty string, the db file is looked for in the same path of okolib.dll
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_LibInit(const char *db_path);

/*!
 * Deallocates any resource that were allocated by oko_LibInitialize
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_LibShutDown();

/*!
 * Print the version of Okolib library on a string passed by reference.
 * @param[out] version	A pointer to a valid string, pre-allocated by the caller, where the version will be stored in.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_LibGetVersion(char *version);

/*!
 * Return a text message that describes the error
 * for the most recent failed call on general library functions.
 * @param[out] errmsg	A pointer to a valid string, pre-allocated by the caller, where the error message will be stored in.
 * @param[in] maxsize	Maximum size of the error message. If bigger, the message will be truncated.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_LibGetLastError(char *errmsg, unsigned int maxsize);


/*!
 * Turn on/off the filter on the USB serial ports
 * @param[in] use	true to turn on the filter, false to torn off it
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_LibSetSuggestedUSBOnly(bool use);


/*!
 * Get the usage status of the USB serial ports filter
 * @param[out] use filter in use (true/false)
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_LibGetSuggestedUSBOnly(bool *use);




/**
 * @}
 * @defgroup Modules Modules
 * @{
*/

/**
* Specifies the available modules types
**/
typedef enum _oko_module_type
{
	OKO_MODULE_TEMP		= 0,	//!< Temperature module
    OKO_MODULE_CO2		= 1,	//!< CO2 module
    OKO_MODULE_O2		= 2,	//!< O2 module
    OKO_MODULE_HMD		= 3,	//!< Humidity module
    OKO_MODULE_MAXNUM			//!< Number of available modules
} oko_module_type;


/*!
 * Detect available modules and open them, returning the type for each module.
 * @param[in] max_num The max number of devices to detect.
 * @param[out] modules: Array of the detected module types.
 * @param[out] detected_num: number of detected modules. It is the length of modules array.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_ModulesDetect(uint32_t max_num, oko_module_type *modules, uint32_t *detected_num);

/*!
 * Detect available modules and open them, returning the type for each module.
 * Keep only modules of interest according to specified module types.
 * @param[in] selected_modules: array of module types to select
 * @param[in] selected_modules_num: length of selected_modules array
 * @param[out] modules: Array of module types.
 * @param[out] detected_num: number of detected modules. It is the length of modules array.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_ModulesDetectSelected(const oko_module_type *selected_modules,
		uint32_t selected_modules_num, oko_module_type *modules, uint32_t *detected_num);

/*!
  * Returns all information about of the specified module of this device,
  * which is not changing during application runtime.
  * @param[in] module: valid module type
  * @param[out] name: name of the module. String needs to be pre-allocated by the caller.
  * @param[out] dim_unit: dimension unit of the module. String needs to be pre-allocated by the caller.
  * @param[out] can_disable: returns, if this module can be disabled or if it is always enabled. '1' for module can be disabled and '0' for module is always enabled.
  * @return OKO_OK upon success, an error code otherwise.
  */
OKO_API oko_res_type OKO_EXPORT
oko_ModuleGetDetails(oko_module_type module, char *name, char *dim_unit, bool *can_disable);

/*!
 * Returns current value of the specified module.
 * @param[in] module: a valid module specifier (@ref oko_module_type)
 * @param[out] current_value: Actual/current value
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_ModuleGetCurrentValue(oko_module_type module, double *current_value);

/*!
 * Returns setpoint value of the specified module of this device.
 * @param[in] module: a valid module specifier (@ref oko_module_type)
 * @param[out] setpoint_value: setpoint value
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_ModuleGetSetpointValue(oko_module_type module, double *setpoint_value);

/*!
 * Returns setpoint limits of the specified module of this device.
 * @param[in] module: a valid module specifier (@ref oko_module_type)
 * @param[out] min_value: minimum value that can be used as setpoint
 * @param[out] max_value: maximum value that can be used as setpoint
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_ModuleGetLimits(oko_module_type module, double *min_value, double *max_value);

/*!
 * Set setpoint of the specified module of this device.
 * @param[in] module: a valid module specifier (@ref oko_module_type)
 * @param[in] setpoint_value: new setpoint for this module, has to be between minimum and maximum of this module.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_ModuleSetSetpointValue(oko_module_type module, double setpoint_value);

/*!
 * Returns enable status of the specified module of this device.
 * - CO2 and Humidity enabled are false when gas flow is off.
 * - O2 module enabled is false when gas flow is off **or** AIR mode is set (CO2 mode).
 * @param[in] module: a valid module specifier (@ref oko_module_type)
 * @param[out] enabled: enabled status (true/false)
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_ModuleGetEnabled(oko_module_type module, bool *enabled);

/*!
 * Set enable status of the specified module of this device.
 * - When CO2 or Humidity is enabled, flow is turned on.
 * - When CO2 or Humidity is disabled, flow is turned off.
 * - When O2 is enabled, CO2/O2 mode is set **and** gas flow is turned on.
 * - When O2 is disabled, CO2 mode is set (AIR mode).
 *
 * @param[in] module: a valid module specifier (@ref oko_module_type)
 * @param[in] enabled: new enable status for this module (true/false)
 * @return OKO_OK upon success, an error code otherwise.
 *
 * \note
 * - CO2 and Humidity modules are enabled/disabled synchronously.
 * - When CO2 or Humidity is disabled, O2 is disabled too.
 * - When CO2 or Humidity is enabled, O2 is enabled **only** if mode is CO2/O2.
 * - When O2 is enabled, CO2 and Humidity are enabled too (if disabled).
 *
 * \warning
 * Some gas devices (eg. CO2-O2 Unit-BL [0-20;1-95]) need a special tubing configuration to work in CO2 mode.
 * Please check the device user manual.
 *
 */
OKO_API oko_res_type OKO_EXPORT
oko_ModuleSetEnabled(oko_module_type module, bool enabled);

/**
 * @}
@defgroup Open Opening, closing and detecting devices
 * @{
*/

/*!
 * Refresh available ports and return the number of them
 * @param[out] count number of available ports
 * @return  OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_LibGetNumberOfPorts(uint32_t *count);

/*!
 * Refresh available ports and return their names
 * @param[out] names array of names, pre-allocated by the caller
 * @return  OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_LibGetPortNames(char **names);

/*!
 * Get the name of the specified port, using the ports refreshed by @ref oko_LibGetNumberOfPorts

 * @param[in] portidx zero-based port index
 * @param[out] portname the specified port name, pre-allocated by the caller.
 * @return  OKO_OK upon success, an error code otherwise.
 * @note It does <b>not</b> refresh available ports, so @ref oko_LibGetNumberOfPorts needs to be called first
 */
OKO_API oko_res_type OKO_EXPORT
oko_LibGetPortName(uint32_t portidx, char *portname);

/*!
 * Open the specified device, returning a handle for it.
 * @param[in] port	The serial port name (eg. 'COM1' on Windows and '/dev/ttyS0' on Linux).
 * @param[out] deviceh The device handle.
 * @return	OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_DeviceOpen(const char *port, uint32_t *deviceh);

/*!
 * Detect available devices and open them, returning an handle for each device.
 * @param[in] max_num The max number of devices to detect.
 * @param[out] devicesh Array of handles to the detected devices.
 * @param[out] detected_num number of detected devices. It is the length of devicesh array.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_DevicesDetect(uint32_t max_num,  uint32_t *devicesh, uint32_t *detected_num);


/*!
 * Detect the first available device matching the filter name and open it, returning a handle for it.
 * @param[in] max_num The max number of devices to detect.
 * @param[out] devicesh Array of handles to the detected devices.
 * @param[in] name_filter The filter on the product name (Product.name). If void no filter will be applied on the name.
 * @param[out] detected_num number of detected devices. It is the length of devicesh array.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_DevicesDetectByName(uint32_t max_num,  uint32_t *devicesh, char *name_filter, uint32_t *detected_num);


/*!
 * Detect the first available device and open it, returning a handle for it.
 * @param[out] deviceh The device handle.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_DeviceDetectSingle(uint32_t *deviceh);


/*!
 * Detect the first available device matching the filter name and open it, returning a handle for it.
 * @param[out] deviceh The device handle.
 * @param[in] name_filter The filter on the product name (Product.name)
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_DeviceDetectSingleByName(uint32_t *deviceh, char *name_filter);


/*!
 * Close the specified device.
 * @param[in] deviceh A valid device handle.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_DeviceClose(uint32_t deviceh);

/*!
 * Return a text message that describes the error
 * for the most recent failed call on a specified device.
 * @param[in] deviceh A valid device handle.
 * @param[out] errmsg	A pointer to a valid string, pre-allocated by the caller, where the error message will be stored in.
 * @param[in] maxsize	Maximum size of the error message. If bigger, the message will be truncated.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_DeviceGetLastError(uint32_t deviceh, char *errmsg, unsigned int maxsize);

/*!
 * Close all the opened devices.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_DevicesCloseAll(void);

/**
 * @}
 * @defgroup Device Device information
 * @{
*/
/*!
 * Get the Port Name of the specified device.
 * @param[in] deviceh A valid device handle.
 * @param[out] port The serial port name, pre-allocated by the caller (eg. 'COM1' on Windows and '/dev/ttyS0' on Linux).
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_DeviceGetPortName(uint32_t deviceh, char *port);


/*!
 * Get the connection status of the current device.
 * @param[in] deviceh A valid device handle.
 * @param[out] conn A value describing the connection status (0=off, 1=0n)
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_DeviceConnectionStatus(uint32_t deviceh, uint32_t *conn);


/*!
 * Set the checksum usage flag of the current device.
 * @param[in] deviceh A valid device handle.
 * @param[in] use_checksum set checksum usage
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_DeviceSetChecksumUsage(uint32_t deviceh, bool use_checksum);


/*!
 * Get the checksum usage flag of the current device.
 * @param[in] deviceh A valid device handle.
 * @param[out] use_checksum variable where is stored the desired info
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_DeviceGetChecksumUsage(uint32_t deviceh, bool *use_checksum);

/*!
 * Check if the communication protocol of the
 * specified device supports checksum.
 * @param[in] deviceh A valid device handle.
 * @param[out] checksum true if checksum is available
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_DeviceGetChecksumAvailable(uint32_t deviceh, bool *checksum);

/**
 * @}
 * @defgroup PropInfo Property information
 * @{
*/

/*!
 * Get the number of available properties of the current device.
 * @param[in] deviceh A valid device handle.
 * @param[out] num The total number of properties for the current device.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertiesGetNumber(uint32_t deviceh, uint32_t *num);

/*!
 * Get the name of the specified property, using its index.
 * Index is zero-based, so the first property has index 0 and the last num-1 (@ref oko_PropertiesGetNumber=
 * This should be used at start to obtain the property names.
 * @param[in] deviceh A valid device handle.
 * @param[in] index Index of the property. Index is zero-based.
 * @param[out] name The name of the property. It needs to be pre-allocated by the caller.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyGetName(uint32_t deviceh, uint32_t index, char *name);

/**
* Specifies the available property types
**/
typedef enum _oko_prop_type
{
    OKO_PROP_UNDEF  = 0,	//!< Undefined property type
    OKO_PROP_STRING = 1,	//!< String property type
    OKO_PROP_INT   	= 2,	//!< Integer (long) property type
    OKO_PROP_DOUBLE = 3,	//!< Double property type
    OKO_PROP_ENUM 	= 4,	//!< Enum property type
} oko_prop_type;


/*!
 * Get the type of the specified property.
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @param[out] prop_type The property type as defined in @ref oko_prop_type.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyGetType(uint32_t deviceh, const char *name, oko_prop_type *prop_type);


/*!
 * Get the number of availables enum values of the specified property.
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @param[out] num pointer to desired value
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyGetEnumNumber(uint32_t deviceh, const char *name, unsigned int *num);



/*!
 * Get the name of the specified enum field of the specified property.
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @param[in] enumh A valid enum handle.
 * @param[out] enumname the desired value. String needs to be pre-allocated by the caller.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyGetEnumName(uint32_t deviceh, const char *name, uint32_t enumh, char *enumname);



/*!
 * Get the measure unit of the specified property.
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name string, pre-allocated by the caller.
 * @param[out] unit Unit of measure string,  pre-allocated by the caller.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyGetUnit(uint32_t deviceh, const char *name, char *unit);

/*!
 * Get the description of the specified property.
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @param[out] desc Description string, pre-allocated by the caller.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyGetDescription(uint32_t deviceh, const char *name, char *desc);

/*!
 * Verify if the specified property is read-only.
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @param[out] read_only TRUE if the property is read-only.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyGetReadOnly(uint32_t deviceh, const char *name, bool *read_only);

/**
* Specifies the available write types
**/
typedef enum _oko_write_type
{
    OKO_WRITE_NONE		= 0,	//!< No write available (read-only)
    OKO_WRITE_EEPROM	= 1,	//!< Only standard write is available
	OKO_WRITE_VOLATILE	= 2,	//!< Only volatile write is available
	OKO_WRITE_BOTH		= 3,	//!< Standard and volatile write types are available
} oko_write_type;


/*!
 * Verify if the specified property is write-only.
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @param[out] write_only TRUE if the property is write-only.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyGetWriteOnly(uint32_t deviceh, const char *name, bool *write_only);

/*!
 * Get the write type of the specified property
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @param[out] write_type The property type as defined in @ref oko_prop_type.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyGetWriteType(uint32_t deviceh, const char *name, oko_write_type *write_type);


/*!
 * Verify if the specified property has readable limits.
 * It can be used with write properties to know if the
 * high and low value can be requested to the device.
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @param[out] has_limits TRUE if the property limits can be read.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyHasLimits(uint32_t deviceh, const char *name, bool *has_limits);

/*!
 * Get the minimum and maximum value that can be set for the specified property.
 * Values are zero if the property has no limits.
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @param[out] min Minimum value that can be set.
 * @param[out] max Maximum value that can be set.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyGetLimits(uint32_t deviceh, const char *name, double *min, double *max);

/*!
 * Verify if the specified property is a main feature of the device.
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @param[out] is_main TRUE if the property is one of the main feature.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyIsMain(uint32_t deviceh, const char *name, bool *is_main);

/*!
 * Verify if the specified property is an advanced feature of the device.
 * Advanced feature should are mainly used for advanced
 * application or debugging.
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @param[out] is_adv TRUE if the property is an advanced feature.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyIsAdvanced(uint32_t deviceh, const char *name, bool *is_adv);

/**
 * @}
 * @defgroup PropRead Reading property value
 * @{
*/


/*!
 * Get the current value of a string property.
 * @param[in] name The property name.
 * @param[in] deviceh A valid device handle.
 * @param[out] val Current value of the specified property. String needs to be pre-allocated by the caller.
 * @return OKO_OK upon success, an error code otherwise.
 * @note It returns the last updated value. To refresh the value, call @ref oko_PropertyUpdate first
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyReadString(uint32_t deviceh, const char *name, char *val);

/*!
 * Get the current value of a integer property.
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @param[out] val Current value of the specified property.
 * @note It returns the last updated value. To refresh the value, call @ref oko_PropertyUpdate first
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyReadInt(uint32_t deviceh, const char *name, int32_t *val);

/*!
 * Get the current value of a double property.
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @param[out] val Current value of the specified property.
 * @return OKO_OK upon success, an error code otherwise.
 * @note It returns the last updated value. To refresh the value, call @ref oko_PropertyUpdate first
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyReadDouble(uint32_t deviceh, const char *name, double *val);

/*!
 * Update the specified property value, reading it from the current device.
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyUpdate(uint32_t deviceh, const char *name);  // refresh, read ?

/**
 * @}
 * @defgroup PropWrite Changing property value
 * @{
*/

/*!
 * Change the current value of the specified string property.
 *
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @param[in] val Value to set.
 * @param[in] async set asynchronous mode on/off (If true, then the value will be wrote as soon as possible but not now)
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyWriteString(uint32_t deviceh, const char *name, const char *val, bool async);

/*!
 * Change the current value of the specified integer property.
 *
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @param[in] val Value to set.
 * @param[in] async (1/0) set asynchronous mode on/off (If 1 then the value will be wrote as soon as possible but not now)
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyWriteInt(uint32_t deviceh, const char *name, int32_t val, char async);

/*!
 * Change the current value of the specified double property.
 *
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @param[in] val Value to set.
 * @param[in] async (1/0) set asynchronous mode on/off (If 1 then the value will be wrote as soon as possible but not now)
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyWriteDouble(uint32_t deviceh, const char *name, double val, char async);

/*!
 * Change the current value of the specified string property in the volatile memory.
 *
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @param[in] val Value to set.
 * @param[in] async set asynchronous mode on/off (If true, then the value will be wrote as soon as possible but not now)
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyWriteVolatileString(uint32_t deviceh, const char *name, const char *val, bool async);

/*!
 * Change the current value of the specified integer property in the volatile memory.
 *
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @param[in] val Value to set.
 * @param[in] async (1/0) set asynchronous mode on/off (If 1 then the value will be wrote as soon as possible but not now)
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyWriteVolatileInt(uint32_t deviceh, const char *name, int32_t val, char async);

/*!
 * Change the current value of the specified double property in the volatile memory.
 *
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @param[in] val Value to set.
 * @param[in] async (1/0) set asynchronous mode on/off (If 1 then the value will be wrote as soon as possible but not now)
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyWriteVolatileDouble(uint32_t deviceh, const char *name, double val, char async);

/**
 * @}
 * @defgroup PropAuto Auto-updating properties
 * @{
*/

/*!
 * Set the update frequency to read the specified property value from the current device.
 * @param[in] deviceh A valid device handle.
 * @param[in] name The property name.
 * @param[in] freq_ms The milliseconds between each property update.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyAutoUpdate(uint32_t deviceh, const char *name, uint32_t freq_ms);

/**
 * @}
 * @defgroup DataLogging Data logging
 * @{
 */

/*!
 * Start the property logging feature for the specified device.
 * @param[in] deviceh A valid device handle.
 * @param[in] filename The file path on which data will be stored.
 * @param[in] freq_ms The milliseconds between each property update.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_StartPropertyLogging(uint32_t deviceh, const char *filename, uint32_t freq_ms);

/*!
 * Stop the property logging feature for the specified device.
 * @param[in] deviceh A valid device handle.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_StopPropertyLogging(uint32_t deviceh);

/*!
 * Get the file path used to log the specified device.
 * @param[in] deviceh A valid device handle.
 * @param[in] filename The file path to which data will be stored.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PropertyLoggingGetFileName(uint32_t deviceh, char *filename);

/**
 * @}
 * @defgroup DataPlayback Data playback
 * @{
 */

/*!
 * Start the playback feature for the specified device.
 * @param[in] deviceh A valid device handle.
 * @param[in] filename The file path from which data will be loaded.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_StartPlayback(uint32_t deviceh, const char *filename);

/*!
 * Start the playback feature for the specified device.
 * @param[in] deviceh A valid device handle.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_StopPlayback(uint32_t deviceh);

/*!
 * Get the file path used to playback the specified device.
 * @param[in] deviceh A valid device handle.
 * @param[out] filename The file path from which data will be loaded. String needs to be pre-allocated by the caller.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_PlaybakGetFileName(uint32_t deviceh, char *filename);

/**
 * @}
 * @defgroup Commands Commands
 * @{
*/


/*!
 * Execute a command
 * @param[in] deviceh A valid device handle.
 * @param[in] name The command name.
 * @return OKO_OK upon success, an error code otherwise.
 */
OKO_API oko_res_type OKO_EXPORT
oko_CommandExecute(uint32_t deviceh, const char *name);


/*! \cond PRIVATE */
OKO_API oko_res_type OKO_EXPORT
oko_OkolabSetDebugPropertiesUsage(bool debug_properties);

OKO_API oko_res_type OKO_EXPORT
oko_OkolabGetLastProtocolError(uint32_t deviceh, const char *name, int *err);
/*! \endcond */

/**
 * @}
*/

#ifdef __cplusplus
}
#endif

#endif /* _OKOLIB_H_ */

