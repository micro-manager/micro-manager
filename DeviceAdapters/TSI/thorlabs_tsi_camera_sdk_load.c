#include "thorlabs_tsi_camera_sdk.h"

#ifndef THORLABS_TSI_BUILD_DLL

TL_CAMERA_SET_FRAME_AVAILABLE_CALLBACK tl_camera_set_frame_available_callback;
TL_CAMERA_SET_CAMERA_CONNECT_CALLBACK tl_camera_set_camera_connect_callback;
TL_CAMERA_SET_CAMERA_DISCONNECT_CALLBACK tl_camera_set_camera_disconnect_callback;
TL_CAMERA_OPEN_SDK tl_camera_open_sdk;
TL_CAMERA_CLOSE_SDK tl_camera_close_sdk;
TL_CAMERA_GET_LAST_ERROR tl_camera_get_last_error;
TL_CAMERA_GET_AVAILABLE_CAMERAS tl_camera_get_available_cameras;
TL_CAMERA_INTERNAL_COMMAND tl_camera_internal_command;
TL_CAMERA_GET_EXPOSURE_US tl_camera_get_exposure_us;
TL_CAMERA_SET_EXPOSURE_US tl_camera_set_exposure_us;
TL_CAMERA_GET_EXPOSURE_RANGE_US tl_camera_get_exposure_range_us;
TL_CAMERA_GET_FIRMWARE_VERSION tl_camera_get_firmware_version;
TL_CAMERA_GET_MEASURED_FRAMES_PER_SECOND tl_camera_get_measured_frames_per_second;
TL_CAMERA_GET_HARDWARE_TRIGGER_MODE tl_camera_get_hardware_trigger_mode;
TL_CAMERA_SET_HARDWARE_TRIGGER_MODE tl_camera_set_hardware_trigger_mode;
TL_CAMERA_GET_HBIN tl_camera_get_hbin;
TL_CAMERA_SET_HBIN tl_camera_set_hbin;
TL_CAMERA_GET_HBIN_RANGE tl_camera_get_hbin_range;
TL_CAMERA_GET_HOT_PIXEL_CORRECTION tl_camera_get_hot_pixel_correction;
TL_CAMERA_SET_HOT_PIXEL_CORRECTION tl_camera_set_hot_pixel_correction;
TL_CAMERA_GET_HOT_PIXEL_CORRECTION_THRESHOLD tl_camera_get_hot_pixel_correction_threshold;
TL_CAMERA_SET_HOT_PIXEL_CORRECTION_THRESHOLD tl_camera_set_hot_pixel_correction_threshold;
TL_CAMERA_GET_HOT_PIXEL_CORRECTION_THRESHOLD_RANGE tl_camera_get_hot_pixel_correction_threshold_range;
TL_CAMERA_GET_IMAGE_WIDTH_PIXELS tl_camera_get_image_width_pixels;
TL_CAMERA_GET_IMAGE_WIDTH_RANGE_PIXELS tl_camera_get_image_width_range_pixels;
TL_CAMERA_GET_IMAGE_HEIGHT_PIXELS tl_camera_get_image_height_pixels;
TL_CAMERA_GET_IMAGE_HEIGHT_RANGE_PIXELS tl_camera_get_image_height_range_pixels;
TL_CAMERA_GET_MODEL tl_camera_get_model;
TL_CAMERA_GET_MODEL_STRING_LENGTH_RANGE tl_camera_get_model_string_length_range;
TL_CAMERA_GET_NAME tl_camera_get_name;
TL_CAMERA_SET_NAME tl_camera_set_name;
TL_CAMERA_GET_NAME_STRING_LENGTH_RANGE tl_camera_get_name_string_length_range;
TL_CAMERA_GET_NUMBER_OF_FRAMES_PER_TRIGGER tl_camera_get_number_of_frames_per_trigger;
TL_CAMERA_SET_NUMBER_OF_FRAMES_PER_TRIGGER tl_camera_set_number_of_frames_per_trigger;
TL_CAMERA_GET_NUMBER_OF_FRAMES_PER_TRIGGER_RANGE tl_camera_get_number_of_frames_per_trigger_range;
TL_CAMERA_GET_DATA_RATE tl_camera_get_data_rate;
TL_CAMERA_SET_DATA_RATE tl_camera_set_data_rate;
TL_CAMERA_GET_PIXEL_SIZE_BYTES tl_camera_get_pixel_size_bytes;
TL_CAMERA_GET_PIXEL_BIT_DEPTH tl_camera_get_pixel_bit_depth;
TL_CAMERA_GET_ROI tl_camera_get_roi;
TL_CAMERA_SET_ROI tl_camera_set_roi;
TL_CAMERA_GET_ROI_RANGE tl_camera_get_roi_range;
TL_CAMERA_GET_SERIAL_NUMBER tl_camera_get_serial_number;
TL_CAMERA_GET_SERIAL_NUMBER_STRING_LENGTH_RANGE tl_camera_get_serial_number_string_length_range;
TL_CAMERA_GET_STATUS_LED tl_camera_get_status_led;
TL_CAMERA_SET_STATUS_LED tl_camera_set_status_led;
TL_CAMERA_GET_EEP_STATUS tl_camera_get_eep_status;
TL_CAMERA_SET_EEP_ENABLED tl_camera_set_eep_enabled;
TL_CAMERA_GET_TEMPERATURE_DEGREES_C tl_camera_get_temperature_degrees_c;
TL_CAMERA_GET_VBIN tl_camera_get_vbin;
TL_CAMERA_SET_VBIN tl_camera_set_vbin;
TL_CAMERA_GET_VBIN_RANGE tl_camera_get_vbin_range;
TL_CAMERA_OPEN_CAMERA tl_camera_open_camera;
TL_CAMERA_CLOSE_CAMERA tl_camera_close_camera;
TL_CAMERA_ARM tl_camera_arm;
TL_CAMERA_ISSUE_SOFTWARE_TRIGGER tl_camera_issue_software_trigger;
TL_CAMERA_DISARM tl_camera_disarm;

typedef void* (*TL_GET_FUNCTION) (char*);
typedef void(*TL_MODULE_INITIALIZE)();
typedef void(*TL_MODULE_UNINITIALIZE)();
typedef void* (*LOAD_MODULE) (const char*);
typedef int(*UNLOAD_MODULE) (void*);
typedef void* (*GET_MODULE_FUNCTION) (void*, const char*);

static void* sdk_handle = 0;
static TL_GET_FUNCTION get_kernel_function = 0;
static TL_MODULE_INITIALIZE kernel_module_initialize = 0;
static TL_MODULE_UNINITIALIZE kernel_module_uninitialize = 0;
static LOAD_MODULE load_module = 0;
static UNLOAD_MODULE unload_module = 0;
static GET_MODULE_FUNCTION get_module_function = 0;

#ifdef _WIN32
#include "windows.h"
#endif

#ifdef _WIN32
static const char* CAMERA_SDK_MODULE_NAME = "thorlabs_tsi_camera_sdk.dll";
static HMODULE kernel_obj = NULL;
#else
// Linux stuff here
#endif

/// <summary>
///     Initializes the camera sdk function pointers to 0.
/// </summary>
static void init_camera_sdk_function_pointers()
{
   tl_camera_set_frame_available_callback = 0;
   tl_camera_set_camera_connect_callback = 0;
   tl_camera_set_camera_disconnect_callback = 0;
   tl_camera_open_sdk = 0;
   tl_camera_close_sdk = 0;
   tl_camera_get_last_error = 0;
   tl_camera_get_available_cameras = 0;
   tl_camera_internal_command = 0;
   tl_camera_get_exposure_us = 0;
   tl_camera_set_exposure_us = 0;
   tl_camera_get_exposure_range_us = 0;
   tl_camera_get_firmware_version = 0;
   tl_camera_get_measured_frames_per_second = 0;
   tl_camera_get_hardware_trigger_mode = 0;
   tl_camera_set_hardware_trigger_mode = 0;
   tl_camera_get_hbin = 0;
   tl_camera_set_hbin = 0;
   tl_camera_get_hbin_range = 0;
   tl_camera_get_hot_pixel_correction = 0;
   tl_camera_set_hot_pixel_correction = 0;
   tl_camera_get_hot_pixel_correction_threshold = 0;
   tl_camera_set_hot_pixel_correction_threshold = 0;
   tl_camera_get_hot_pixel_correction_threshold_range = 0;
   tl_camera_get_image_width_pixels = 0;
   tl_camera_get_image_width_range_pixels = 0;
   tl_camera_get_image_height_pixels = 0;
   tl_camera_get_image_height_range_pixels = 0;
   tl_camera_get_model = 0;
   tl_camera_get_model_string_length_range = 0;
   tl_camera_get_name = 0;
   tl_camera_set_name = 0;
   tl_camera_get_name_string_length_range = 0;
   tl_camera_get_number_of_frames_per_trigger = 0;
   tl_camera_set_number_of_frames_per_trigger = 0;
   tl_camera_get_number_of_frames_per_trigger_range = 0;
   tl_camera_get_data_rate = 0;
   tl_camera_set_data_rate = 0;
   tl_camera_get_pixel_size_bytes = 0;
   tl_camera_get_pixel_bit_depth = 0;
   tl_camera_get_roi = 0;
   tl_camera_set_roi = 0;
   tl_camera_get_roi_range = 0;
   tl_camera_get_serial_number = 0;
   tl_camera_get_serial_number_string_length_range = 0;
   tl_camera_get_status_led = 0;
   tl_camera_set_status_led = 0;
   tl_camera_get_eep_status = 0;
   tl_camera_set_eep_enabled = 0;
   tl_camera_get_temperature_degrees_c = 0;
   tl_camera_get_vbin = 0;
   tl_camera_set_vbin = 0;
   tl_camera_get_vbin_range = 0;
   tl_camera_open_camera = 0;
   tl_camera_close_camera = 0;
   tl_camera_arm = 0;
   tl_camera_issue_software_trigger = 0;
   tl_camera_disarm = 0;
}

static int init_error_cleanup()
{
   if (sdk_handle)
      if (unload_module)
         unload_module(sdk_handle);

   sdk_handle = 0;
   if (kernel_obj != NULL)
      FreeLibrary(kernel_obj);
   kernel_obj = NULL;

   get_kernel_function = 0;
   kernel_module_initialize = 0;
   kernel_module_uninitialize = 0;
   load_module = 0;
   unload_module = 0;
   get_module_function = 0;

   init_camera_sdk_function_pointers();

   return (1);
}

/// <summary>
///     Loads the DLL and maps all the functions so that they can be called directly.
/// </summary>
/// <returns>
///     1 for error, 0 for success
/// </returns>
int init_camera_sdk_dll(void)
{
   init_camera_sdk_function_pointers();

   // Platform specific code to get a handle to the SDK kernel module.
#ifdef _WIN32
   kernel_obj = LoadLibrary ("thorlabs_unified_sdk_kernel.dll");
   if (!kernel_obj)
   {
      return (init_error_cleanup());
   }
   get_kernel_function = (TL_GET_FUNCTION)(GetProcAddress(kernel_obj, (char*) "tl_get_function"));
   if (!get_kernel_function)
   {
      return (init_error_cleanup());
   }
   kernel_module_initialize = (TL_MODULE_INITIALIZE)(GetProcAddress(kernel_obj, (char*) "tl_module_initialize"));
   if (!kernel_module_initialize)
   {
      return (init_error_cleanup());
   }
   kernel_module_uninitialize = (TL_MODULE_UNINITIALIZE)(GetProcAddress(kernel_obj, (char*) "tl_module_uninitialize"));
   if (!kernel_module_uninitialize)
   {
      return (init_error_cleanup());
   }
#else
// Linux specific stuff
#endif

   // Initialize the kernel module.
   kernel_module_initialize();
   load_module = (LOAD_MODULE)get_kernel_function("load_module");
   unload_module = (UNLOAD_MODULE)get_kernel_function("unload_module");
   get_module_function = (GET_MODULE_FUNCTION)get_kernel_function("get_module_function");

   // Load camera SDK module.
   sdk_handle = load_module(CAMERA_SDK_MODULE_NAME);
   if (!sdk_handle)
   {
      return (init_error_cleanup());
   }

   tl_camera_set_frame_available_callback = (TL_CAMERA_SET_FRAME_AVAILABLE_CALLBACK)get_module_function(sdk_handle, "tl_camera_set_frame_available_callback");
   if (!tl_camera_set_frame_available_callback)
   {
      return (init_error_cleanup());
   }

   tl_camera_set_camera_connect_callback = (TL_CAMERA_SET_CAMERA_CONNECT_CALLBACK)get_module_function(sdk_handle, "tl_camera_set_camera_connect_callback");
   if (!tl_camera_set_camera_connect_callback)
   {
      return (init_error_cleanup());
   }

   tl_camera_set_camera_disconnect_callback = (TL_CAMERA_SET_CAMERA_DISCONNECT_CALLBACK)get_module_function(sdk_handle, "tl_camera_set_camera_disconnect_callback");
   if (!tl_camera_set_camera_disconnect_callback)
   {
      return (init_error_cleanup());
   }

   tl_camera_open_sdk = (TL_CAMERA_OPEN_SDK)get_module_function(sdk_handle, "tl_camera_open_sdk");
   if (!tl_camera_open_sdk)
   {
      return (init_error_cleanup());
   }

   tl_camera_close_sdk = (TL_CAMERA_CLOSE_SDK)get_module_function(sdk_handle, "tl_camera_close_sdk");
   if (!tl_camera_close_sdk)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_last_error = (TL_CAMERA_GET_LAST_ERROR)get_module_function(sdk_handle, "tl_camera_get_last_error");
   if (!tl_camera_get_last_error)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_available_cameras = (TL_CAMERA_GET_AVAILABLE_CAMERAS)get_module_function(sdk_handle, "tl_camera_get_available_cameras");
   if (!tl_camera_get_available_cameras)
   {
      return (init_error_cleanup());
   }

   tl_camera_internal_command = (TL_CAMERA_INTERNAL_COMMAND)get_module_function(sdk_handle, "tl_camera_internal_command");
   if (!tl_camera_internal_command)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_exposure_us = (TL_CAMERA_GET_EXPOSURE_US)get_module_function(sdk_handle, "tl_camera_get_exposure_us");
   if (!tl_camera_get_exposure_us)
   {
      return (init_error_cleanup());
   }

   tl_camera_set_exposure_us = (TL_CAMERA_SET_EXPOSURE_US)get_module_function(sdk_handle, "tl_camera_set_exposure_us");
   if (!tl_camera_set_exposure_us)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_exposure_range_us = (TL_CAMERA_GET_EXPOSURE_RANGE_US)get_module_function(sdk_handle, "tl_camera_get_exposure_range_us");
   if (!tl_camera_get_exposure_range_us)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_firmware_version = (TL_CAMERA_GET_FIRMWARE_VERSION)get_module_function(sdk_handle, "tl_camera_get_firmware_version");
   if (!tl_camera_get_firmware_version)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_measured_frames_per_second = (TL_CAMERA_GET_MEASURED_FRAMES_PER_SECOND)get_module_function(sdk_handle, "tl_camera_get_measured_frames_per_second");
   if (!tl_camera_get_measured_frames_per_second)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_hardware_trigger_mode = (TL_CAMERA_GET_HARDWARE_TRIGGER_MODE)get_module_function(sdk_handle, "tl_camera_get_hardware_trigger_mode");
   if (!tl_camera_get_hardware_trigger_mode)
   {
      return (init_error_cleanup());
   }

   tl_camera_set_hardware_trigger_mode = (TL_CAMERA_SET_HARDWARE_TRIGGER_MODE)get_module_function(sdk_handle, "tl_camera_set_hardware_trigger_mode");
   if (!tl_camera_set_hardware_trigger_mode)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_hbin = (TL_CAMERA_GET_HBIN)get_module_function(sdk_handle, "tl_camera_get_hbin");
   if (!tl_camera_get_hbin)
   {
      return (init_error_cleanup());
   }

   tl_camera_set_hbin = (TL_CAMERA_SET_HBIN)get_module_function(sdk_handle, "tl_camera_set_hbin");
   if (!tl_camera_set_hbin)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_hbin_range = (TL_CAMERA_GET_HBIN_RANGE)get_module_function(sdk_handle, "tl_camera_get_hbin_range");
   if (!tl_camera_get_hbin_range)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_hot_pixel_correction = (TL_CAMERA_GET_HOT_PIXEL_CORRECTION)get_module_function(sdk_handle, "tl_camera_get_hot_pixel_correction");
   if (!tl_camera_get_hot_pixel_correction)
   {
      return (init_error_cleanup());
   }

   tl_camera_set_hot_pixel_correction = (TL_CAMERA_SET_HOT_PIXEL_CORRECTION)get_module_function(sdk_handle, "tl_camera_set_hot_pixel_correction");
   if (!tl_camera_set_hot_pixel_correction)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_hot_pixel_correction_threshold = (TL_CAMERA_GET_HOT_PIXEL_CORRECTION_THRESHOLD)get_module_function(sdk_handle, "tl_camera_get_hot_pixel_correction_threshold");
   if (!tl_camera_get_hot_pixel_correction_threshold)
   {
      return (init_error_cleanup());
   }

   tl_camera_set_hot_pixel_correction_threshold = (TL_CAMERA_SET_HOT_PIXEL_CORRECTION_THRESHOLD)get_module_function(sdk_handle, "tl_camera_set_hot_pixel_correction_threshold");
   if (!tl_camera_set_hot_pixel_correction_threshold)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_hot_pixel_correction_threshold_range = (TL_CAMERA_GET_HOT_PIXEL_CORRECTION_THRESHOLD_RANGE)get_module_function(sdk_handle, "tl_camera_get_hot_pixel_correction_threshold_range");
   if (!tl_camera_get_hot_pixel_correction_threshold_range)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_image_width_pixels = (TL_CAMERA_GET_IMAGE_WIDTH_PIXELS)get_module_function(sdk_handle, "tl_camera_get_image_width_pixels");
   if (!tl_camera_get_image_width_pixels)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_image_width_range_pixels = (TL_CAMERA_GET_IMAGE_WIDTH_RANGE_PIXELS)get_module_function(sdk_handle, "tl_camera_get_image_width_range_pixels");
   if (!tl_camera_get_image_width_range_pixels)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_image_height_pixels = (TL_CAMERA_GET_IMAGE_HEIGHT_PIXELS)get_module_function(sdk_handle, "tl_camera_get_image_height_pixels");
   if (!tl_camera_get_image_height_pixels)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_image_height_range_pixels = (TL_CAMERA_GET_IMAGE_HEIGHT_RANGE_PIXELS)get_module_function(sdk_handle, "tl_camera_get_image_height_range_pixels");
   if (!tl_camera_get_image_height_range_pixels)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_model = (TL_CAMERA_GET_MODEL)get_module_function(sdk_handle, "tl_camera_get_model");
   if (!tl_camera_get_model)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_model_string_length_range = (TL_CAMERA_GET_MODEL_STRING_LENGTH_RANGE)get_module_function(sdk_handle, "tl_camera_get_model_string_length_range");
   if (!tl_camera_get_model_string_length_range)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_name = (TL_CAMERA_GET_NAME)get_module_function(sdk_handle, "tl_camera_get_name");
   if (!tl_camera_get_name)
   {
      return (init_error_cleanup());
   }

   tl_camera_set_name = (TL_CAMERA_SET_NAME)get_module_function(sdk_handle, "tl_camera_set_name");
   if (!tl_camera_set_name)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_name_string_length_range = (TL_CAMERA_GET_NAME_STRING_LENGTH_RANGE)get_module_function(sdk_handle, "tl_camera_get_name_string_length_range");
   if (!tl_camera_get_name_string_length_range)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_number_of_frames_per_trigger = (TL_CAMERA_GET_NUMBER_OF_FRAMES_PER_TRIGGER)get_module_function(sdk_handle, "tl_camera_get_number_of_frames_per_trigger");
   if (!tl_camera_get_number_of_frames_per_trigger)
   {
      return (init_error_cleanup());
   }

   tl_camera_set_number_of_frames_per_trigger = (TL_CAMERA_SET_NUMBER_OF_FRAMES_PER_TRIGGER)get_module_function(sdk_handle, "tl_camera_set_number_of_frames_per_trigger");
   if (!tl_camera_set_number_of_frames_per_trigger)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_number_of_frames_per_trigger_range = (TL_CAMERA_GET_NUMBER_OF_FRAMES_PER_TRIGGER_RANGE)get_module_function(sdk_handle, "tl_camera_get_number_of_frames_per_trigger_range");
   if (!tl_camera_get_number_of_frames_per_trigger_range)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_data_rate = (TL_CAMERA_GET_DATA_RATE)get_module_function(sdk_handle, "tl_camera_get_data_rate");
   if (!tl_camera_get_data_rate)
   {
      return (init_error_cleanup());
   }

   tl_camera_set_data_rate = (TL_CAMERA_SET_DATA_RATE)get_module_function(sdk_handle, "tl_camera_set_data_rate");
   if (!tl_camera_set_data_rate)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_pixel_size_bytes = (TL_CAMERA_GET_PIXEL_SIZE_BYTES)get_module_function(sdk_handle, "tl_camera_get_pixel_size_bytes");
   if (!tl_camera_get_pixel_size_bytes)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_pixel_bit_depth = (TL_CAMERA_GET_PIXEL_BIT_DEPTH)get_module_function(sdk_handle, "tl_camera_get_pixel_bit_depth");
   if (!tl_camera_get_pixel_bit_depth)
   {
       return (init_error_cleanup());
   }

   tl_camera_get_roi = (TL_CAMERA_GET_ROI)get_module_function(sdk_handle, "tl_camera_get_roi");
   if (!tl_camera_get_roi)
   {
      return (init_error_cleanup());
   }

   tl_camera_set_roi = (TL_CAMERA_SET_ROI)get_module_function(sdk_handle, "tl_camera_set_roi");
   if (!tl_camera_set_roi)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_roi_range = (TL_CAMERA_GET_ROI_RANGE)get_module_function(sdk_handle, "tl_camera_get_roi_range");
   if (!tl_camera_get_roi_range)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_serial_number = (TL_CAMERA_GET_SERIAL_NUMBER)get_module_function(sdk_handle, "tl_camera_get_serial_number");
   if (!tl_camera_get_serial_number)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_serial_number_string_length_range = (TL_CAMERA_GET_SERIAL_NUMBER_STRING_LENGTH_RANGE)get_module_function(sdk_handle, "tl_camera_get_serial_number_string_length_range");
   if (!tl_camera_get_serial_number_string_length_range)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_status_led = (TL_CAMERA_GET_STATUS_LED)get_module_function(sdk_handle, "tl_camera_get_status_led");
   if (!tl_camera_get_status_led)
   {
      return (init_error_cleanup());
   }

   tl_camera_set_status_led = (TL_CAMERA_SET_STATUS_LED)get_module_function(sdk_handle, "tl_camera_set_status_led");
   if (!tl_camera_set_status_led)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_eep_status = (TL_CAMERA_GET_EEP_STATUS)get_module_function(sdk_handle, "tl_camera_get_eep_status");
   if (!tl_camera_get_eep_status)
   {
      return (init_error_cleanup());
   }

   tl_camera_set_eep_enabled = (TL_CAMERA_SET_EEP_ENABLED)get_module_function(sdk_handle, "tl_camera_set_eep_enabled");
   if (!tl_camera_set_eep_enabled)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_temperature_degrees_c = (TL_CAMERA_GET_TEMPERATURE_DEGREES_C)get_module_function(sdk_handle, "tl_camera_get_temperature_degrees_c");
   if (!tl_camera_get_temperature_degrees_c)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_vbin = (TL_CAMERA_GET_VBIN)get_module_function(sdk_handle, "tl_camera_get_vbin");
   if (!tl_camera_get_vbin)
   {
      return (init_error_cleanup());
   }

   tl_camera_set_vbin = (TL_CAMERA_SET_VBIN)get_module_function(sdk_handle, "tl_camera_set_vbin");
   if (!tl_camera_set_vbin)
   {
      return (init_error_cleanup());
   }

   tl_camera_get_vbin_range = (TL_CAMERA_GET_VBIN_RANGE)get_module_function(sdk_handle, "tl_camera_get_vbin_range");
   if (!tl_camera_get_vbin_range)
   {
      return (init_error_cleanup());
   }

   tl_camera_open_camera = (TL_CAMERA_OPEN_CAMERA)get_module_function(sdk_handle, "tl_camera_open_camera");
   if (!tl_camera_open_camera)
   {
      return (init_error_cleanup());
   }

   tl_camera_close_camera = (TL_CAMERA_CLOSE_CAMERA)get_module_function(sdk_handle, "tl_camera_close_camera");
   if (!tl_camera_close_camera)
   {
      return (init_error_cleanup());
   }

   tl_camera_arm = (TL_CAMERA_ARM)get_module_function(sdk_handle, "tl_camera_arm");
   if (!tl_camera_arm)
   {
      return (init_error_cleanup());
   }

   tl_camera_issue_software_trigger = (TL_CAMERA_ISSUE_SOFTWARE_TRIGGER)get_module_function(sdk_handle, "tl_camera_issue_software_trigger");
   if (!tl_camera_issue_software_trigger)
   {
      return (init_error_cleanup());
   }

   tl_camera_disarm = (TL_CAMERA_DISARM)get_module_function(sdk_handle, "tl_camera_disarm");
   if (!tl_camera_disarm)
   {
      return (init_error_cleanup());
   }

   return (0);
}

int free_camera_sdk_dll(void)
{
   init_camera_sdk_function_pointers();

   // Free the camera SDK module.
   if (sdk_handle)
      unload_module(sdk_handle);
   sdk_handle = 0;

   // Free the kernel module.
   if (kernel_module_uninitialize)
      kernel_module_uninitialize();

#ifdef _WIN32
   if (kernel_obj != NULL)
   {
      FreeLibrary(kernel_obj);
      kernel_obj = NULL;
   }
#else

#endif
   get_kernel_function = 0;
   kernel_module_initialize = 0;
   kernel_module_uninitialize = 0;
   load_module = 0;
   unload_module = 0;
   get_module_function = 0;

   return (0);
}

#endif
