#pragma once

//#ifdef __cplusplus
//extern "C"
//{
//#endif
	
/// <summary>
/// Loads and initializes the SDK DLL.
/// </summary>
/// <returns>0 if successful or a positive integer error code to indicate failure.</returns>
int tl_camera_sdk_dll_initialize(void);

/// <summary>
/// Cleans up and terminates the SDK DLL.
/// </summary>
/// <returns>0 if successful or a positive integer error code to indicate failure.</returns>
int tl_camera_sdk_dll_terminate(void);

//#ifdef __cplusplus
//};
//#endif