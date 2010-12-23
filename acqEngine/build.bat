del /Q classes
mkdir classes
copy -R src/org classes/
java -cp ../../3rdpartypublic/classext/clojure.jar;../MMCoreJ_wrap/MMCoreJ.jar;../../3rdpartypublic/classext/ij.jar;../mmstudio/MMJ_.jar;./src -Dclojure.compile.path=classes clojure.lang.Compile org.micromanager.acq-engine
jar cvf MMAcqEngine.jar -C classes/ .    
copy MMAcqEngine.jar "/Program Files/ImageJ/plugins/Micro-Manager"
copy MMAcqEngine.jar "../bin_Win32/"
copy MMAcqEngine.jar "../bin_Win64/"
copy MMAcqEngine.jar "../Install_Win32/micro-manager/plugins/Micro-Manager"
