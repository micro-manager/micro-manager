# Micro-Manager

Micro-Manager is an application to control microscope hardware, such as cameras,
xy-stages, filter wheels, etc. It includes a hardware abstraction layer written
in C++ and a user interface written in Java (Swing).

**Go to [micro-manager.org](https://micro-manager.org) for documentation and
binary downloads.**

**For support, see [Micro-Manager
Community](https://micro-manager.org/Micro-Manager_Community).**

## Source code

This repository contains the Java projects that make up the Micro-Manager
"MMStudio" GUI application. The device control layer is written in C++ and found
in a separate repository,
[mmCoreAndDevices](https://github.com/micro-manager/mmCoreAndDevices),
which is currently a git submodule of this repository.

To checkout both repositories together:

```sh
git clone --recurse-submodules https://github.com/micro-manager/micro-manager.git
```

### Branches

- `main` - the main branch of development (Micro-Manager 2.x)
- `svn-mirror` - git-svn mirror of the Micro-Manager 1.4 Subversion repository

Other branches are not official.

## Developer information

For license information, please see [doc/copyright.txt](doc/copyright.txt).

For build instructions, please see the [doc/how-to-build.md](doc/how-to-build.md).

Additional information is available on the Micro-Manager website at
https://micro-manager.org

## Contributing

Start here: https://micro-manager.org/Building_and_debugging_Micro-Manager_source_code
