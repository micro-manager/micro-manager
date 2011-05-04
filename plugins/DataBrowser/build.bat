rmdir classes
md classes
xcopy /E /Y src classes\
"%JDK6%\bin\java" -cp src;classes;../../../3rdpartypublic/classext/clojure.jar;../../../3rdpartypublic/classext/clojure-contrib.jar;../../../3rdpartypublic/classext/ij.jar;../../../3rdpartypublic/classext/bsh-2.0b4.jar;../../mmstudio/MMJ_.jar;../../MMCoreJ_wrap/MMCoreJ.jar;../../acqEngine/MMAcqEngine.jar -Dclojure.compile.path=classes -server clojure.lang.Compile org.micromanager.browser.plugin
"%JDK6%\bin\jar" cvf DataBrowser.jar -C classes\ .  
copy /Y DataBrowser.jar "\Program Files\ImageJ\mmplugins\"
copy /Y DataBrowser.jar ..\..\Install_AllPlatforms\micro-manager\mmplugins\
