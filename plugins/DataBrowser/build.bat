rmdir classes
md classes
xcopy /E /Y src classes\
"%JAVA_HOME%\bin\java" -cp ../../../3rdpartypublic/classext/clojure.jar;../../../3rdpartypublic/classext/clojure-contrib.jar;../../../3rdpartypublic/classext/ij.jar;../../../3rdpartypublic/classext/bsh-2.0b4.jar;../../mmstudio/MMJ_.jar;../../acqEngine/MMAcqEngine.jar;src -Dclojure.compile.path=classes -server clojure.lang.Compile org.micromanager.browser.plugin
"%JAVA_HOME%\bin\jar" cvf DataBrowser.jar -C classes\ .  
copy /Y DataBrowser.jar \Program Files\ImageJ\plugins\Micro-Manager
copy /Y DataBrowser.jar ..\..\bin_Win32\
copy /Y DataBrowser.jar ..\..\bin_x64\
copy /Y DataBrowser.jar ..\..\Install_Win32\micro-manager\plugins\Micro-Manager"
copy /Y DataBrowser.jar ..\..\Install_x64\micro-manager\plugins\Micro-Manager"