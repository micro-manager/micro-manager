# MicroManager Device Adapter for Spinnaker SDK (Point Grey) Cameras

## About

These device adapters bring support for Point Grey/FLIR Cameras that leverage the Spinnaker SDK. Their goal is not to provide access to every setting available to the cameras but instead to provide access to the settings most commonly used in a microscopy environment. The adapters were originally developed for the Chameleon 3 range of cameras, however, they have since been tested with the Blackfly S and Grasshopper 3 ranges. The adapters have been developed and tested on 64-bit Windows 7 and 10.

## Compilation Prerequisites 

In addition to MicroManager's usual prerequisites, the adapters require a version of the Visual Studio 2010 Spinnaker SDK to be installed on the system. They also expect a copy of the Spinnaker SDK's "include", "lib" and "lib64" folders to be placed in the 3rdparty folder in a Spinnaker subdirectory. The adapters have been designed to work with Spinnaker SDK version 1.20, however, in theory it should be possible to compile against newer versions of the SDK with minimal (if any) changes to the code. The versions of the adapters shipped with the MicroManager nightly builds are compiled against version 1.20 of the SDK.

## Support

For support with installation and use of the cameras/adapters in MicroManager please see the [Cairn Research](https://www.cairn-research.co.uk/) website


## Development

These device adapters were initially developed by Elliot Steele as part of a final year masters project in Electrical and Electronic Engineering at Imperial College London. The project was undertaken with the Photonics Group in Imperial College's Department of Physics and in conjunction with Cairn Research Ltd. Since the completion of the project maintenance has continued and the drivers have been made freely available by Cairn Research. The code was integrated into MicroManager's source on TODO

Special acknowledgements and thanks go to Professor Paul French and the rest of the Photonics Group for hosting the project and to Cairn Research for distribution of the device adapters before their integration into the MicroManager project and their continued support in the adapters' development. 
