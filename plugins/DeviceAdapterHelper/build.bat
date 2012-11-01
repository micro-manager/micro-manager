rem We need Java 6 for this plugin. If you have Java 6, installed, set an environment variable, JDK6, to its location.
if exist classes rmdir classes /s /q
mkdir classes
xcopy /E /y src classes\
java -cp src;classes;../../../3rdpartypublic/classext/*;../../mmstudio/MMJ_.jar;../../MMCoreJ_wrap/MMCoreJ.jar;../../acqEngine/MMAcqEngine.jar -Djava.library.path=../../bin_Win32 -Dclojure.compile.path=classes -server clojure.lang.Compile DeviceAdapterHelper.plugin
jar cf DLLAutoReloader.jar -C classes\ .  
copy /Y DLLAutoReloader.jar ..\..\Install_AllPlatforms\micro-manager\mmplugins\
