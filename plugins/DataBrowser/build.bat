rem We need Java 6 for this plugin. If you have Java 6, installed, set an environment variable, JDK6, to its location.
if not "%JDK6%"=="" (
if exist classes rmdir classes /s /q
mkdir classes
xcopy /E /y src classes\
"%JDK6%\bin\java" -cp src;classes;../../../3rdpartypublic/classext/clojure.jar;../../../3rdpartypublic/classext/data.json.jar;../../../3rdpartypublic/classext/ij.jar;../../../3rdpartypublic/classext/bsh-2.0b4.jar;../../mmstudio/MMJ_.jar;../../MMCoreJ_wrap/MMCoreJ.jar;../../acqEngine/MMAcqEngine.jar -Djava.library.path=../../bin_Win32 -Dclojure.compile.path=classes -server clojure.lang.Compile org.micromanager.browser.plugin
"%JDK6%\bin\jar" cf DataBrowser.jar -C classes\ .  
copy /Y DataBrowser.jar ..\..\Install_AllPlatforms\micro-manager\mmplugins\
)