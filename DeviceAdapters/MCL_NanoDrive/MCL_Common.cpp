// MCL headers
#include "Madlib.h"
#include "MCL_Common.h"
#include "MCL_NanoDrive.h"

// List/heap headers
#include "handle_list_if.h"
#include "HandleListType.h"

static int ChooseAvailableXYStageAxes(unsigned short pid, unsigned char axisBitmap, int handle, bool useStrictChoices);

static int ChooseAvailableZStageAxis(unsigned short pid, unsigned char axisBitmap, int handle, bool useStrictChoices);

static bool FindMatchingDevice(int deviceAdapterType, int &deviceAdapterHandle, int &deviceAdapterAxis);

static bool FindMatchingDeviceInList(int deviceAdapterType, int *handles, int handlesCount, bool useStrictMatcingCriteria, int &deviceAdapterHandle, int &deviceAdapterAxis);

static void ReleaseUnusedDevices();


int AcquireDeviceHandle(int deviceType, int &deviceAdapterHandle, int &deviceAdapterAxis)
{
	deviceAdapterAxis = 0;
	deviceAdapterHandle = 0;

	// Attempt to find a device that can perform the deviceType role.
	bool foundDevice = FindMatchingDevice(deviceType, deviceAdapterHandle, deviceAdapterAxis);
	if (foundDevice)
	{
		// If we found a device add it to our list.
		HandleListType newDeviceAdapter(deviceAdapterHandle, deviceType, deviceAdapterAxis, (deviceType == Z_TYPE ? 0 : (deviceAdapterAxis + 1)));
		HandleListAddToLockedList(newDeviceAdapter);
	}

	// Release devices that are not needed.
	ReleaseUnusedDevices();

	return foundDevice ? MCL_SUCCESS : MCL_INVALID_HANDLE;
}

bool FindMatchingDevice(int deviceAdapterType, int &deviceAdapterHandle, int &deviceAdapterAxis)
{
	bool foundDevice = false;
	deviceAdapterHandle = 0;
	deviceAdapterAxis = 0;

	// First search through our existing devices to find appropriate matching axes.
	int existingHandlesCount = MCL_NumberOfCurrentHandles();
	if (existingHandlesCount != 0)
	{
		int *existingHandles = new int[existingHandlesCount];
		existingHandlesCount = MCL_GetAllHandles(existingHandles, existingHandlesCount);
		foundDevice = FindMatchingDeviceInList(deviceAdapterType, existingHandles, existingHandlesCount, true, deviceAdapterHandle, deviceAdapterAxis);
		delete[] existingHandles;

		if (foundDevice)
			return true;
	}

	// Next search through all available Nano-Drive systems.
	int handlesCount = MCL_GrabAllHandles();
	if (handlesCount == 0)
	{
		return false;
	}
	int* handles = new int[handlesCount];
	handlesCount = MCL_GetAllHandles(handles, handlesCount);
	foundDevice = FindMatchingDeviceInList(deviceAdapterType, handles, handlesCount, true, deviceAdapterHandle, deviceAdapterAxis);

	// Lastly if we have not found the device, search through all available Nano-Drive systems with relaxed criteria.
	if (!foundDevice)
	{
		foundDevice = FindMatchingDeviceInList(deviceAdapterType, handles, handlesCount, false, deviceAdapterHandle, deviceAdapterAxis);
	}
	delete[] handles;

	return foundDevice;
}

bool FindMatchingDeviceInList(int deviceAdapterType, int *handles, int handlesCount, bool useStrictMatcingCriteria, int &deviceAdapterHandle, int &deviceAdapterAxis)
{
	deviceAdapterAxis = 0;
	for (int ii = 0; ii < handlesCount; ii++)
	{
		ProductInformation pi;
		if (MCL_GetProductInfo(&pi, handles[ii]) != MCL_SUCCESS)
			continue;

		if (deviceAdapterType == XY_TYPE)
			deviceAdapterAxis = ChooseAvailableXYStageAxes(pi.Product_id, pi.axis_bitmap, handles[ii], useStrictMatcingCriteria);
		else if (deviceAdapterType == Z_TYPE)
			deviceAdapterAxis = ChooseAvailableZStageAxis(pi.Product_id, pi.axis_bitmap, handles[ii], useStrictMatcingCriteria);

		if (deviceAdapterAxis != 0)
		{
			deviceAdapterHandle = handles[ii];
		}
	}
	return deviceAdapterAxis != 0;
}

void ReleaseUnusedDevices()
{
	int existingHandlesCount = MCL_NumberOfCurrentHandles();
	if (existingHandlesCount != 0)
	{
		int *existingHandles = new int[existingHandlesCount];
		existingHandlesCount = MCL_GetAllHandles(existingHandles, existingHandlesCount);

		// Iterate through the devices and release those that are not in use.
		for (int i = 0; i < existingHandlesCount; i++)
		{
			if (!HandleExistsOnLockedList(existingHandles[i]))
			{
				MCL_ReleaseHandle(existingHandles[i]);
			}
		}
		delete[] existingHandles;
	}
}

int ChooseAvailableXYStageAxes(unsigned short pid, unsigned char axisBitmap, int handle, bool useStrictChoices)
{
	int ordersize = 2;
	int order[] = { XAXIS, 0 };
	int strictOrder[] = { XAXIS, 0 };

	switch (pid)
	{
		// These devices should not be used as a XY Stage device.
		case NANODRIVE_FX_Z_ENCODER:
		case NANOGAUGE_FX2:
		case NANODRIVE_FX2_DDS:
		case NANODRIVE_FX_1AXIS:
		case NANODRIVE_FX2_1AXIS:
		case NANODRIVE_FX2_1AXIS_20:
		case NANODRIVE_FX2_CFOCUS:
			return 0;

		// Four axis systems can have two XY Stage adapters.
		case NANODRIVE_FX2_4AXIS:
			order[1] = ZAXIS;
			break;

		// Use the standard order.
		default:
			break;
	}

	int *chosenOrder = useStrictChoices ? strictOrder : order;
	int axis = 0;
	for (int ii = 0; ii < ordersize; ii++)
	{
		if (chosenOrder[ii] == 0)
			break;

		// Check that both axes are valid.
		int xBitmap = 0x1 << (chosenOrder[ii] - 1);
		int yBitmap = 0x1 << chosenOrder[ii];
		if (((axisBitmap & xBitmap) != xBitmap) ||
			((axisBitmap & yBitmap) != yBitmap))
			continue;

		HandleListType device(handle, XY_TYPE, chosenOrder[ii], chosenOrder[ii] + 1);
		if (HandleExistsOnLockedList(device) == false)
		{
			axis = chosenOrder[ii];
			break;
		}
	}
	return axis;
}

int ChooseAvailableZStageAxis(unsigned short pid, unsigned char axisBitmap, int handle, bool useStrictChoices)
{
	int ordersize = 4;
	int order[] = { ZAXIS, AAXIS, XAXIS, YAXIS };
	int strictOrder[] = { ZAXIS, AAXIS, 0, 0 };

	switch (pid)
	{
		// These devices should not be used as Z Stage devices.
		case NANODRIVE_FX_Z_ENCODER:
		case NANOGAUGE_FX2:
		case NANODRIVE_FX2_DDS:
			return 0;
		// Single axis devices should use the default order for their strict implementation.
		case NANODRIVE_FX_1AXIS:
		case NANODRIVE_FX2_1AXIS:
		case NANODRIVE_FX2_1AXIS_20:
		case NANODRIVE_FX2_CFOCUS:
			strictOrder[2] = XAXIS;
			strictOrder[3] = YAXIS;
			break;
		// In all other cases uses the standard order.
		default:
			break;
	}

	int *chosenOrder = useStrictChoices ? strictOrder : order;
	int axis = 0;
	for (int ii = 0; ii < ordersize; ii++)
	{
		if (chosenOrder[ii] == 0)
			break;

		// Check that the axis is valid.
		int bitmap = 0x1 << (chosenOrder[ii] - 1);
		if ((axisBitmap & bitmap) != bitmap)
			continue;

		// Check if a matching device is already in our list of controlled devices.
		HandleListType device(handle, Z_TYPE, chosenOrder[ii], 0);
		if (HandleExistsOnLockedList(device) == false)
		{
			// If there is no conflict we can choose 
			axis = chosenOrder[ii];
			break;
		}
	}
	return axis;
}