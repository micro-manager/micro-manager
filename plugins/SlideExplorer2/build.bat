rem We need Java 6 for this plugin. If you have Java 6, installed, set an environment variable, JDK6, to its location.
if exist classes rmdir classes /s /q
if exist build rmdir build /s /q
mkdir classes
mkdir build
mkdir 
xcopy /E /y src classes\
java -cp src;classes;../../../3rdpartypublic/classext/clojure.jar;../../../3rdpartypublic/classext/core.cache.jar;../../../3rdpartypublic/classext/core.memoize.jar;../../../3rdpartypublic/classext/ij.jar;../../../3rdpartypublic/classext/bsh-2.0b4.jar;../../mmstudio/MMJ_.jar;../../MMCoreJ_wrap/MMCoreJ.jar;../../acqEngine/MMAcqEngine.jar -Djava.library.path=../../bin_x64 -Dclojure.compile.path=classes -server clojure.lang.Compile slide-explorer.plugin
xcopy /E /y src build\
xcopy classes\org\micromanager\SlideExplorer2.class build\org\micromanager\
jar cf SlideExplorer2.jar -C build\ .  
copy /Y SlideExplorer2.jar ..\..\Install_AllPlatforms\micro-manager\mmplugins\
