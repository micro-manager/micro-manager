del *.ilk /s/q/f
del *.ncb /s/q/f
del mmgr_dal*.dll /s/q/f


xcopy /c /y /r ..\..\DeviceAdapters\DemoCamera\Win32\Release\mmgr_dal_DemoCamera.dll win32\Release
xcopy /c /y /r ..\..\DeviceAdapters\SimpleAutofocus\Win32\Release\mmgr_dal_SimpleAutofocus.dll win32\Release

xcopy /c /y /r ..\..\DeviceAdapters\DemoCamera\x64\Release\mmgr_dal_DemoCamera.dll x64\Release
xcopy /c /y /r ..\..\DeviceAdapters\SimpleAutofocus\x64\Release\mmgr_dal_SimpleAutofocus.dll x64\Release


xcopy /c /y /r ..\..\DeviceAdapters\DemoCamera\Win32\Debug\mmgr_dal_DemoCamera.dll win32\Debug
xcopy /c /y /r ..\..\DeviceAdapters\SimpleAutofocus\Win32\Debug\mmgr_dal_SimpleAutofocus.dll win32\Debug

xcopy /c /y /r ..\..\DeviceAdapters\DemoCamera\x64\Debug\mmgr_dal_DemoCamera.dll x64\Debug
xcopy /c /y /r ..\..\DeviceAdapters\SimpleAutofocus\x64\Debug\mmgr_dal_SimpleAutofocus.dll x64\Debug
