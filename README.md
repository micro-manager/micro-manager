## Micro-Manager 

Micro-Manager is an application to control microscope hardware, such as cameras, xy-stages, filter wheels, etc..  It includes a hardware abstraction layer written in C++ and a user interface written in Java (Swing).  User and developer documentation can be found at https://micro-manager.org.

The master branch on https://github.com/micro-manager/micro-manager is used to build version 2.0-gamma (binaries can be download at https://micro-manager.org/wiki/Download%20Micro-Manager_Latest%20Release). The branch "svn-mirror" tracks the subversion repository at https://valelab4.ucsf.edu/svn/micromanager2/trunk/ that contains the source code for Micro-Manager 1.4.  

To get the source code follow these instructions:

    Clone the repository: git clone https://github.com/micro-manager/micro-manager.git
    Move Git bash into the repository: cd micro-manager
    Make sure any submodules are also cloned: git submodule update --init --recursive

If you are working only with the source code that is publicly available then that's all. Some vendors do not allow us to make public device adapters written for their equipment.  If you are part of the micro-manager group, you can get access to these "secret" device adpaters by:

    Move Git bash into the mmCoreAndDevices submodule: cd mmCoreAndDevices
    Change to the privateMain branch: git checkout privateMain
    privateMain has a submodule that main does not. Make sure that any new submodules are in a consistent state: git submodule update --init --recursive


For licensing information, please see **doc/copyright.txt.**

For build instructions, please see the **doc/how-to-build.md.**

Additional information is available on the Micro-Manager website at
https://micro-manager.org

### Contributing
Want to help make Micro-Manager better?  
Get started here: https://micro-manager.org/wiki/How_to_debug_and_develop_MM2.0
