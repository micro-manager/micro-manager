if exist classes rmdir classes /s /q
mkdir classes
xcopy /E /y src classes\
"%JAVA_HOME%\bin\java" -cp src;classes;../../../3rdpartypublic/classext/clojure.jar;../../../3rdpartypublic/classext/clojure-contrib.jar;../../../3rdpartypublic/classext/ij.jar;../../../3rdpartypublic/classext/bsh-2.0b4.jar;../../mmstudio/MMJ_.jar;../../MMCoreJ_wrap/MMCoreJ.jar;../../acqEngine/MMAcqEngine.jar -Djava.library.path=../../bin_Win32 -Dclojure.compile.path=classes -server clojure.lang.Compile org.micromanager.browser.plugin
"%JAVA_HOME%\bin\jar" cvf DataBrowser.jar -C classes\ .  
copy /Y DataBrowser.jar ..\..\Install_AllPlatforms\micro-manager\mmplugins\
