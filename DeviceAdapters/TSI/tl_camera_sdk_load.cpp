#include "tl_camera_sdk.h"

#ifndef THORLABS_TSI_BUILD_DLL

TL_CAMERA_SET_FRAME_AVAILABLE_CALLBACK tl_camera_set_frame_available_callback;
TL_CAMERA_SET_CAMERA_CONNECT_CALLBACK tl_camera_set_camera_connect_callback;
TL_CAMERA_SET_CAMERA_DISCONNECT_CALLBACK tl_camera_set_camera_disconnect_callback;
TL_CAMERA_OPEN_SDK tl_camera_open_sdk;
TL_CAMERA_CLOSE_SDK tl_camera_close_sdk;
TL_CAMERA_GET_LAST_ERROR tl_camera_get_last_error;
TL_CAMERA_DISCOVER_AVAILABLE_CAMERAS tl_camera_discover_available_cameras;
_INTERNAL_COMMAND _internal_command;
TL_CAMERA_GET_EXPOSURE_TIME tl_camera_get_exposure_time;
TL_CAMERA_SET_EXPOSURE_TIME tl_camera_set_exposure_time;
TL_CAMERA_GET_EXPOSURE_TIME_RANGE tl_camera_get_exposure_time_range;
TL_CAMERA_GET_FIRMWARE_VERSION tl_camera_get_firmware_version;
TL_CAMERA_GET_FRAME_TIME tl_camera_get_frame_time;
TL_CAMERA_GET_MEASURED_FRAME_RATE tl_camera_get_measured_frame_rate;
TL_CAMERA_GET_TRIGGER_POLARITY tl_camera_get_trigger_polarity;
TL_CAMERA_SET_TRIGGER_POLARITY tl_camera_set_trigger_polarity;
TL_CAMERA_GET_BINX tl_camera_get_binx;
TL_CAMERA_SET_BINX tl_camera_set_binx;
TL_CAMERA_GET_IS_OPERATION_MODE_SUPPORTED tl_camera_get_is_operation_mode_supported;
TL_CAMERA_GET_OPERATION_MODE tl_camera_get_operation_mode;
TL_CAMERA_SET_OPERATION_MODE tl_camera_set_operation_mode;
TL_CAMERA_GET_IS_ARMED tl_camera_get_is_armed;
TL_CAMERA_GET_IS_EEP_SUPPORTED tl_camera_get_is_eep_supported;
TL_CAMERA_GET_IS_LED_SUPPORTED tl_camera_get_is_led_supported;
TL_CAMERA_GET_IS_DATA_RATE_SUPPORTED tl_camera_get_is_data_rate_supported;
TL_CAMERA_GET_COLOR_CORRECTION_MATRIX tl_camera_get_color_correction_matrix;
TL_CAMERA_GET_DEFAULT_WHITE_BALANCE_MATRIX tl_camera_get_default_white_balance_matrix;
TL_CAMERA_GET_CAMERA_SENSOR_TYPE tl_camera_get_camera_sensor_type;
TL_CAMERA_GET_COLOR_FILTER_ARRAY_PHASE tl_camera_get_color_filter_array_phase;
TL_CAMERA_GET_CAMERA_COLOR_CORRECTION_MATRIX_OUTPUT_COLOR_SPACE tl_camera_get_camera_color_correction_matrix_output_color_space;
TL_CAMERA_GET_IS_COOLING_SUPPORTED tl_camera_get_is_cooling_supported;
TL_CAMERA_GET_IS_TAPS_SUPPORTED tl_camera_get_is_taps_supported;
TL_CAMERA_GET_IS_NIR_BOOST_SUPPORTED tl_camera_get_is_nir_boost_supported;
TL_CAMERA_GET_BINX_RANGE tl_camera_get_binx_range;
TL_CAMERA_GET_IS_HOT_PIXEL_CORRECTION_ENABLED tl_camera_get_is_hot_pixel_correction_enabled;
TL_CAMERA_SET_IS_HOT_PIXEL_CORRECTION_ENABLED tl_camera_set_is_hot_pixel_correction_enabled;
TL_CAMERA_GET_HOT_PIXEL_CORRECTION_THRESHOLD tl_camera_get_hot_pixel_correction_threshold;
TL_CAMERA_SET_HOT_PIXEL_CORRECTION_THRESHOLD tl_camera_set_hot_pixel_correction_threshold;
TL_CAMERA_GET_HOT_PIXEL_CORRECTION_THRESHOLD_RANGE tl_camera_get_hot_pixel_correction_threshold_range;
TL_CAMERA_GET_SENSOR_WIDTH tl_camera_get_sensor_width;
TL_CAMERA_GET_GAIN_RANGE tl_camera_get_gain_range;
TL_CAMERA_GET_SENSOR_HEIGHT tl_camera_get_sensor_height;
TL_CAMERA_GET_MODEL tl_camera_get_model;
TL_CAMERA_GET_MODEL_STRING_LENGTH_RANGE tl_camera_get_model_string_length_range;
TL_CAMERA_GET_NAME tl_camera_get_name;
TL_CAMERA_SET_NAME tl_camera_set_name;
TL_CAMERA_GET_NAME_STRING_LENGTH_RANGE tl_camera_get_name_string_length_range;
TL_CAMERA_GET_FRAMES_PER_TRIGGER_ZERO_FOR_UNLIMITED tl_camera_get_frames_per_trigger_zero_for_unlimited;
TL_CAMERA_SET_FRAMES_PER_TRIGGER_ZERO_FOR_UNLIMITED tl_camera_set_frames_per_trigger_zero_for_unlimited;
TL_CAMERA_GET_FRAMES_PER_TRIGGER_RANGE tl_camera_get_frames_per_trigger_range;
TL_CAMERA_GET_DATA_RATE tl_camera_get_data_rate;
TL_CAMERA_SET_DATA_RATE tl_camera_set_data_rate;
TL_CAMERA_GET_SENSOR_PIXEL_SIZE_BYTES tl_camera_get_sensor_pixel_size_bytes;
TL_CAMERA_GET_SENSOR_PIXEL_WIDTH tl_camera_get_sensor_pixel_width;
TL_CAMERA_GET_SENSOR_PIXEL_HEIGHT tl_camera_get_sensor_pixel_height;
TL_CAMERA_GET_BIT_DEPTH tl_camera_get_bit_depth;
TL_CAMERA_GET_ROI tl_camera_get_roi;
TL_CAMERA_SET_ROI tl_camera_set_roi;
TL_CAMERA_GET_ROI_RANGE tl_camera_get_roi_range;
TL_CAMERA_GET_SERIAL_NUMBER tl_camera_get_serial_number;
TL_CAMERA_GET_SERIAL_NUMBER_STRING_LENGTH_RANGE tl_camera_get_serial_number_string_length_range;
TL_CAMERA_GET_IS_LED_ON tl_camera_get_is_led_on;
TL_CAMERA_SET_IS_LED_ON tl_camera_set_is_led_on;
TL_CAMERA_GET_USB_PORT_TYPE tl_camera_get_usb_port_type;
TL_CAMERA_GET_COMMUNICATION_INTERFACE tl_camera_get_communication_Interface;
TL_CAMERA_GET_EEP_STATUS tl_camera_get_eep_status;
TL_CAMERA_SET_IS_EEP_ENABLED tl_camera_set_is_eep_enabled;
TL_CAMERA_GET_BINY tl_camera_get_biny;
TL_CAMERA_SET_BINY tl_camera_set_biny;
TL_CAMERA_GET_GAIN tl_camera_get_gain;
TL_CAMERA_SET_GAIN tl_camera_set_gain;
TL_CAMERA_GET_BLACK_LEVEL tl_camera_get_black_level;
TL_CAMERA_SET_BLACK_LEVEL tl_camera_set_black_level;
TL_CAMERA_GET_BLACK_LEVEL_RANGE tl_camera_get_black_level_range;
TL_CAMERA_GET_BINY_RANGE tl_camera_get_biny_range;
TL_CAMERA_GET_SENSOR_READOUT_TIME tl_camera_get_sensor_readout_time;
TL_CAMERA_GET_IMAGE_WIDTH tl_camera_get_image_width;
TL_CAMERA_GET_IMAGE_HEIGHT tl_camera_get_image_height;
TL_CAMERA_GET_IMAGE_WIDTH_RANGE tl_camera_get_image_width_range;
TL_CAMERA_GET_IMAGE_HEIGHT_RANGE tl_camera_get_image_height_range;
TL_CAMERA_OPEN_CAMERA tl_camera_open_camera;
TL_CAMERA_CLOSE_CAMERA tl_camera_close_camera;
TL_CAMERA_GET_IMAGE_POLL_TIMEOUT tl_camera_get_image_poll_timeout;
TL_CAMERA_SET_IMAGE_POLL_TIMEOUT tl_camera_set_image_poll_timeout;
TL_CAMERA_GET_PENDING_FRAME_OR_NULL tl_camera_get_pending_frame_or_null;
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

static const char* CAMERA_SDK_MODULE_NAME = "thorlabs_tsi_camera_sdk.dll";
static HMODULE kernel_obj = NULL;
#endif

#ifdef __linux__
#include "dlfcn.h"

static const char* CAMERA_SDK_MODULE_NAME = "libthorlabs_tsi_camera_sdk.so";
static void* kernel_obj = 0;
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
	tl_camera_discover_available_cameras = 0;
	_internal_command = 0;
	tl_camera_get_exposure_time = 0;
	tl_camera_set_exposure_time = 0;
	tl_camera_get_exposure_time_range = 0;
	tl_camera_get_firmware_version = 0;
	tl_camera_get_frame_time = 0;
	tl_camera_get_measured_frame_rate = 0;
	tl_camera_get_trigger_polarity = 0;
	tl_camera_set_trigger_polarity = 0;
	tl_camera_get_binx = 0;
	tl_camera_set_binx = 0;
	tl_camera_get_binx_range = 0;
	tl_camera_get_camera_sensor_type = 0;
	tl_camera_get_is_operation_mode_supported = 0;
	tl_camera_get_operation_mode = 0;
	tl_camera_set_operation_mode = 0;
	tl_camera_get_is_armed = 0;
	tl_camera_get_is_eep_supported = 0;
	tl_camera_get_is_led_supported = 0;
	tl_camera_get_is_data_rate_supported = 0;
	tl_camera_get_is_cooling_supported = 0;
	tl_camera_get_is_taps_supported = 0;
	tl_camera_get_is_nir_boost_supported = 0;
	tl_camera_get_color_correction_matrix = 0;
	tl_camera_get_default_white_balance_matrix = 0;
	tl_camera_get_color_filter_array_phase = 0;
	tl_camera_get_camera_color_correction_matrix_output_color_space = 0;
	tl_camera_get_is_hot_pixel_correction_enabled = 0;
	tl_camera_set_is_hot_pixel_correction_enabled = 0;
	tl_camera_get_hot_pixel_correction_threshold = 0;
	tl_camera_set_hot_pixel_correction_threshold = 0;
	tl_camera_get_hot_pixel_correction_threshold_range = 0;
	tl_camera_get_sensor_width = 0;
	tl_camera_get_gain_range = 0;
	tl_camera_get_sensor_height = 0;
	tl_camera_get_model = 0;
	tl_camera_get_model_string_length_range = 0;
	tl_camera_get_name = 0;
	tl_camera_set_name = 0;
	tl_camera_get_name_string_length_range = 0;
	tl_camera_get_frames_per_trigger_zero_for_unlimited = 0;
	tl_camera_set_frames_per_trigger_zero_for_unlimited = 0;
	tl_camera_get_frames_per_trigger_range = 0;
	tl_camera_get_data_rate = 0;
	tl_camera_set_data_rate = 0;
	tl_camera_get_sensor_pixel_size_bytes = 0;
	tl_camera_get_sensor_pixel_width = 0;
	tl_camera_get_sensor_pixel_height = 0;
	tl_camera_get_bit_depth = 0;
	tl_camera_get_roi = 0;
	tl_camera_set_roi = 0;
	tl_camera_get_roi_range = 0;
	tl_camera_get_serial_number = 0;
	tl_camera_get_serial_number_string_length_range = 0;
	tl_camera_get_is_led_on = 0;
	tl_camera_set_is_led_on = 0;
	tl_camera_get_usb_port_type = 0;
	tl_camera_get_communication_Interface = 0;
	tl_camera_get_eep_status = 0;
	tl_camera_set_is_eep_enabled = 0;
	tl_camera_get_biny = 0;
	tl_camera_set_biny = 0;
	tl_camera_get_biny_range = 0;
	tl_camera_get_gain = 0;
	tl_camera_set_gain = 0;
	tl_camera_get_black_level = 0;
	tl_camera_set_black_level = 0;
	tl_camera_get_black_level_range = 0;
	tl_camera_get_exposure_time_range = 0;
	tl_camera_get_sensor_readout_time = 0;
	tl_camera_get_image_width = 0;
	tl_camera_get_image_height = 0;
	tl_camera_get_image_width_range = 0;
	tl_camera_get_image_height_range = 0;
	tl_camera_open_camera = 0;
	tl_camera_close_camera = 0;
	tl_camera_get_pending_frame_or_null = 0;
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

#ifdef _WIN32
	if (kernel_obj != NULL)
		FreeLibrary(kernel_obj);
	kernel_obj = NULL;
#endif

#ifdef __linux__
	if (kernel_obj)
		dlclose (kernel_obj);
	kernel_obj = 0;
#endif

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
int tl_camera_sdk_dll_initialize(void)
{
	init_camera_sdk_function_pointers();

	// Platform specific code to get a handle to the SDK kernel module.
#ifdef _WIN32
	kernel_obj = LoadLibraryA("thorlabs_unified_sdk_kernel.dll");
	int lastError = GetLastError();
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
#endif

#ifdef __linux__
	kernel_obj = dlopen ("libthorlabs_unified_sdk_kernel.so", RTLD_LAZY);
	if (!kernel_obj)
	{
		return (init_error_cleanup());
	}
	get_kernel_function = (TL_GET_FUNCTION)(dlsym (kernel_obj, (char*) "tl_get_function"));
	if (!get_kernel_function)
	{
		return (init_error_cleanup());
	}
	kernel_module_initialize = (TL_MODULE_INITIALIZE)(dlsym (kernel_obj, (char*) "tl_module_initialize"));
	if (!kernel_module_initialize)
	{
		return (init_error_cleanup());
	}
	kernel_module_uninitialize = (TL_MODULE_UNINITIALIZE)(dlsym (kernel_obj, (char*) "tl_module_uninitialize"));
	if (!kernel_module_uninitialize)
	{
		return (init_error_cleanup());
	}
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

	tl_camera_discover_available_cameras = (TL_CAMERA_DISCOVER_AVAILABLE_CAMERAS)get_module_function(sdk_handle, "tl_camera_discover_available_cameras");
	if (!tl_camera_discover_available_cameras)
	{
		return (init_error_cleanup());
	}

	_internal_command = (_INTERNAL_COMMAND)get_module_function(sdk_handle, "_internal_command");
	if (!_internal_command)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_exposure_time = (TL_CAMERA_GET_EXPOSURE_TIME)get_module_function(sdk_handle, "tl_camera_get_exposure_time");
	if (!tl_camera_get_exposure_time)
	{
		return (init_error_cleanup());
	}

	tl_camera_set_exposure_time = (TL_CAMERA_SET_EXPOSURE_TIME)get_module_function(sdk_handle, "tl_camera_set_exposure_time");
	if (!tl_camera_set_exposure_time)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_image_poll_timeout = (TL_CAMERA_GET_IMAGE_POLL_TIMEOUT)get_module_function(sdk_handle, "tl_camera_get_image_poll_timeout");
	if (!tl_camera_get_image_poll_timeout)
	{
		return (init_error_cleanup());
	}

	tl_camera_set_image_poll_timeout = (TL_CAMERA_SET_IMAGE_POLL_TIMEOUT)get_module_function(sdk_handle, "tl_camera_set_image_poll_timeout");
	if (!tl_camera_set_image_poll_timeout)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_pending_frame_or_null = (TL_CAMERA_GET_PENDING_FRAME_OR_NULL)get_module_function(sdk_handle, "tl_camera_get_pending_frame_or_null");
	if (!tl_camera_get_pending_frame_or_null)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_exposure_time_range = (TL_CAMERA_GET_EXPOSURE_RANGE)get_module_function(sdk_handle, "tl_camera_get_exposure_time_range");
	if (!tl_camera_get_exposure_time_range)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_firmware_version = (TL_CAMERA_GET_FIRMWARE_VERSION)get_module_function(sdk_handle, "tl_camera_get_firmware_version");
	if (!tl_camera_get_firmware_version)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_frame_time = (TL_CAMERA_GET_FRAME_TIME)get_module_function(sdk_handle, "tl_camera_get_frame_time");
	if (!tl_camera_get_frame_time)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_measured_frame_rate = (TL_CAMERA_GET_MEASURED_FRAME_RATE)get_module_function(sdk_handle, "tl_camera_get_measured_frame_rate");
	if (!tl_camera_get_measured_frame_rate)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_trigger_polarity = (TL_CAMERA_GET_TRIGGER_POLARITY)get_module_function(sdk_handle, "tl_camera_get_trigger_polarity");
	if (!tl_camera_get_trigger_polarity)
	{
		return (init_error_cleanup());
	}

	tl_camera_set_trigger_polarity = (TL_CAMERA_SET_TRIGGER_POLARITY)get_module_function(sdk_handle, "tl_camera_set_trigger_polarity");
	if (!tl_camera_set_trigger_polarity)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_binx = (TL_CAMERA_GET_BINX)get_module_function(sdk_handle, "tl_camera_get_binx");
	if (!tl_camera_get_binx)
	{
		return (init_error_cleanup());
	}

	tl_camera_set_binx = (TL_CAMERA_SET_BINX)get_module_function(sdk_handle, "tl_camera_set_binx");
	if (!tl_camera_set_binx)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_binx_range = (TL_CAMERA_GET_BINX_RANGE)get_module_function(sdk_handle, "tl_camera_get_binx_range");
	if (!tl_camera_get_binx_range)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_is_hot_pixel_correction_enabled = (TL_CAMERA_GET_IS_HOT_PIXEL_CORRECTION_ENABLED)get_module_function(sdk_handle, "tl_camera_get_is_hot_pixel_correction_enabled");
	if (!tl_camera_get_is_hot_pixel_correction_enabled)
	{
		return (init_error_cleanup());
	}

	tl_camera_set_is_hot_pixel_correction_enabled = (TL_CAMERA_SET_IS_HOT_PIXEL_CORRECTION_ENABLED)get_module_function(sdk_handle, "tl_camera_set_is_hot_pixel_correction_enabled");
	if (!tl_camera_set_is_hot_pixel_correction_enabled)
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

	tl_camera_get_sensor_width = (TL_CAMERA_GET_SENSOR_WIDTH)get_module_function(sdk_handle, "tl_camera_get_sensor_width");
	if (!tl_camera_get_sensor_width)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_gain_range = (TL_CAMERA_GET_GAIN_RANGE)get_module_function(sdk_handle, "tl_camera_get_gain_range");
	if (!tl_camera_get_gain_range)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_sensor_height = (TL_CAMERA_GET_SENSOR_HEIGHT)get_module_function(sdk_handle, "tl_camera_get_sensor_height");
	if (!tl_camera_get_sensor_height)
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

	tl_camera_get_frames_per_trigger_zero_for_unlimited = (TL_CAMERA_GET_FRAMES_PER_TRIGGER_ZERO_FOR_UNLIMITED)get_module_function(sdk_handle, "tl_camera_get_frames_per_trigger_zero_for_unlimited");
	if (!tl_camera_get_frames_per_trigger_zero_for_unlimited)
	{
		return (init_error_cleanup());
	}

	tl_camera_set_frames_per_trigger_zero_for_unlimited = (TL_CAMERA_SET_FRAMES_PER_TRIGGER_ZERO_FOR_UNLIMITED)get_module_function(sdk_handle, "tl_camera_set_frames_per_trigger_zero_for_unlimited");
	if (!tl_camera_set_frames_per_trigger_zero_for_unlimited)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_frames_per_trigger_range = (TL_CAMERA_GET_FRAMES_PER_TRIGGER_RANGE)get_module_function(sdk_handle, "tl_camera_get_frames_per_trigger_range");
	if (!tl_camera_get_frames_per_trigger_range)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_camera_sensor_type = (TL_CAMERA_GET_CAMERA_SENSOR_TYPE)get_module_function(sdk_handle, "tl_camera_get_camera_sensor_type");
	if (!tl_camera_get_camera_sensor_type)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_is_operation_mode_supported = (TL_CAMERA_GET_IS_OPERATION_MODE_SUPPORTED)get_module_function(sdk_handle, "tl_camera_get_is_operation_mode_supported");
	if (!tl_camera_get_is_operation_mode_supported)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_is_eep_supported = (TL_CAMERA_GET_IS_EEP_SUPPORTED)get_module_function(sdk_handle, "tl_camera_get_is_eep_supported");
	if (!tl_camera_get_is_eep_supported)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_is_led_supported = (TL_CAMERA_GET_IS_LED_SUPPORTED)get_module_function(sdk_handle, "tl_camera_get_is_led_supported");
	if (!tl_camera_get_is_led_supported)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_is_data_rate_supported = (TL_CAMERA_GET_IS_DATA_RATE_SUPPORTED)get_module_function(sdk_handle, "tl_camera_get_is_data_rate_supported");
	if (!tl_camera_get_is_data_rate_supported)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_is_cooling_supported = (TL_CAMERA_GET_IS_COOLING_SUPPORTED)get_module_function(sdk_handle, "tl_camera_get_is_cooling_supported");
	if (!tl_camera_get_is_cooling_supported)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_is_taps_supported = (TL_CAMERA_GET_IS_TAPS_SUPPORTED)get_module_function(sdk_handle, "tl_camera_get_is_taps_supported");
	if (!tl_camera_get_is_taps_supported)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_is_nir_boost_supported = (TL_CAMERA_GET_IS_NIR_BOOST_SUPPORTED)get_module_function(sdk_handle, "tl_camera_get_is_nir_boost_supported");
	if (!tl_camera_get_is_nir_boost_supported)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_is_armed = (TL_CAMERA_GET_IS_ARMED)get_module_function(sdk_handle, "tl_camera_get_is_armed");
	if (!tl_camera_get_is_armed)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_operation_mode = (TL_CAMERA_GET_OPERATION_MODE)get_module_function(sdk_handle, "tl_camera_get_operation_mode");
	if (!tl_camera_get_operation_mode)
	{
		return (init_error_cleanup());
	}

	tl_camera_set_operation_mode = (TL_CAMERA_SET_OPERATION_MODE)get_module_function(sdk_handle, "tl_camera_set_operation_mode");
	if (!tl_camera_set_operation_mode)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_default_white_balance_matrix = (TL_CAMERA_GET_DEFAULT_WHITE_BALANCE_MATRIX)get_module_function(sdk_handle, "tl_camera_get_default_white_balance_matrix");
	if (!tl_camera_get_default_white_balance_matrix)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_color_filter_array_phase = (TL_CAMERA_GET_COLOR_FILTER_ARRAY_PHASE)get_module_function(sdk_handle, "tl_camera_get_color_filter_array_phase");
	if (!tl_camera_get_color_filter_array_phase)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_color_correction_matrix = (TL_CAMERA_GET_COLOR_CORRECTION_MATRIX)get_module_function(sdk_handle, "tl_camera_get_color_correction_matrix");
	if (!tl_camera_get_color_correction_matrix)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_camera_color_correction_matrix_output_color_space = (TL_CAMERA_GET_CAMERA_COLOR_CORRECTION_MATRIX_OUTPUT_COLOR_SPACE)get_module_function(sdk_handle, "tl_camera_get_camera_color_correction_matrix_output_color_space");
	if (!tl_camera_get_camera_color_correction_matrix_output_color_space)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_is_hot_pixel_correction_enabled = (TL_CAMERA_GET_IS_HOT_PIXEL_CORRECTION_ENABLED)get_module_function(sdk_handle, "tl_camera_get_is_hot_pixel_correction_enabled");
	if (!tl_camera_get_is_hot_pixel_correction_enabled)
	{
		return (init_error_cleanup());
	}

	tl_camera_set_is_hot_pixel_correction_enabled = (TL_CAMERA_SET_IS_HOT_PIXEL_CORRECTION_ENABLED)get_module_function(sdk_handle, "tl_camera_set_is_hot_pixel_correction_enabled");
	if (!tl_camera_set_is_hot_pixel_correction_enabled)
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

	tl_camera_get_sensor_pixel_size_bytes = (TL_CAMERA_GET_SENSOR_PIXEL_SIZE_BYTES)get_module_function(sdk_handle, "tl_camera_get_sensor_pixel_size_bytes");
	if (!tl_camera_get_sensor_pixel_size_bytes)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_sensor_pixel_width = (TL_CAMERA_GET_SENSOR_PIXEL_WIDTH)get_module_function(sdk_handle, "tl_camera_get_sensor_pixel_width");
	if (!tl_camera_get_sensor_pixel_width)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_sensor_pixel_height = (TL_CAMERA_GET_SENSOR_PIXEL_HEIGHT)get_module_function(sdk_handle, "tl_camera_get_sensor_pixel_height");
	if (!tl_camera_get_sensor_pixel_height)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_bit_depth = (TL_CAMERA_GET_BIT_DEPTH)get_module_function(sdk_handle, "tl_camera_get_bit_depth");
	if (!tl_camera_get_bit_depth)
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

	tl_camera_get_is_led_on = (TL_CAMERA_GET_IS_LED_ON)get_module_function(sdk_handle, "tl_camera_get_is_led_on");
	if (!tl_camera_get_is_led_on)
	{
		return (init_error_cleanup());
	}

	tl_camera_set_is_led_on = (TL_CAMERA_SET_IS_LED_ON)get_module_function(sdk_handle, "tl_camera_set_is_led_on");
	if (!tl_camera_set_is_led_on)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_usb_port_type = (TL_CAMERA_GET_USB_PORT_TYPE)get_module_function(sdk_handle, "tl_camera_get_usb_port_type");
	if (!tl_camera_get_usb_port_type)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_communication_Interface = (TL_CAMERA_GET_COMMUNICATION_INTERFACE)get_module_function(sdk_handle, "tl_camera_get_communication_Interface");
	if (!tl_camera_get_communication_Interface)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_eep_status = (TL_CAMERA_GET_EEP_STATUS)get_module_function(sdk_handle, "tl_camera_get_eep_status");
	if (!tl_camera_get_eep_status)
	{
		return (init_error_cleanup());
	}

	tl_camera_set_is_eep_enabled = (TL_CAMERA_SET_IS_EEP_ENABLED)get_module_function(sdk_handle, "tl_camera_set_is_eep_enabled");
	if (!tl_camera_set_is_eep_enabled)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_biny = (TL_CAMERA_GET_BINY)get_module_function(sdk_handle, "tl_camera_get_biny");
	if (!tl_camera_get_biny)
	{
		return (init_error_cleanup());
	}

	tl_camera_set_biny = (TL_CAMERA_SET_BINY)get_module_function(sdk_handle, "tl_camera_set_biny");
	if (!tl_camera_set_biny)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_gain = (TL_CAMERA_GET_GAIN)get_module_function(sdk_handle, "tl_camera_get_gain");
	if (!tl_camera_get_gain)
	{
		return (init_error_cleanup());
	}

	tl_camera_set_gain = (TL_CAMERA_SET_GAIN)get_module_function(sdk_handle, "tl_camera_set_gain");
	if (!tl_camera_set_gain)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_black_level = (TL_CAMERA_GET_BLACK_LEVEL)get_module_function(sdk_handle, "tl_camera_get_black_level");
	if (!tl_camera_get_black_level)
	{
		return (init_error_cleanup());
	}

	tl_camera_set_black_level = (TL_CAMERA_SET_BLACK_LEVEL)get_module_function(sdk_handle, "tl_camera_set_black_level");
	if (!tl_camera_set_black_level)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_black_level_range = (TL_CAMERA_GET_BLACK_LEVEL_RANGE)get_module_function(sdk_handle, "tl_camera_get_black_level_range");
	if (!tl_camera_get_black_level_range)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_biny_range = (TL_CAMERA_GET_BINY_RANGE)get_module_function(sdk_handle, "tl_camera_get_biny_range");
	if (!tl_camera_get_biny_range)
	{
		return (init_error_cleanup());
	}

	tl_camera_get_exposure_time_range = (TL_CAMERA_GET_EXPOSURE_TIME_RANGE)get_module_function(sdk_handle, "tl_camera_get_exposure_time_range");
	if (!tl_camera_get_exposure_time_range)
	{
		return (init_error_cleanup());
	}
	
	tl_camera_get_sensor_readout_time = (TL_CAMERA_GET_SENSOR_READOUT_TIME)get_module_function(sdk_handle, "tl_camera_get_sensor_readout_time");
	if (!tl_camera_get_sensor_readout_time)
	{
		return (init_error_cleanup());
	}
	
	tl_camera_get_image_width = (TL_CAMERA_GET_IMAGE_WIDTH)get_module_function(sdk_handle, "tl_camera_get_image_width");
	if (!tl_camera_get_image_width)
	{
		return (init_error_cleanup());
	}
	
	tl_camera_get_image_height = (TL_CAMERA_GET_IMAGE_HEIGHT)get_module_function(sdk_handle, "tl_camera_get_image_height");
	if (!tl_camera_get_image_height)
	{
		return (init_error_cleanup());
	}
	
	tl_camera_get_image_width_range = (TL_CAMERA_GET_IMAGE_WIDTH_RANGE)get_module_function(sdk_handle, "tl_camera_get_image_width_range");
	if (!tl_camera_get_image_width_range)
	{
		return (init_error_cleanup());
	}
	
	tl_camera_get_image_height_range = (TL_CAMERA_GET_IMAGE_HEIGHT_RANGE)get_module_function(sdk_handle, "tl_camera_get_image_height_range");
	if (!tl_camera_get_image_height_range)
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

int tl_camera_sdk_dll_terminate(void)
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
#endif

#ifdef __linux__
	if (kernel_obj)
	{
		dlclose (kernel_obj);
		kernel_obj = 0;
	}
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
