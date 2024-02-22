## Micro-Manager Microscenery plugin

For orginal readme see parent repo.

### How to build

- do the prequisites of [https://micro-manager.org/Writing_plugins_for_Micro-Manager]
- build microscenery-core and copy "..\core\build\libs\microscenery-core-1.0-SNAPSHOT.jar" to "'\dependencies\artifacts\compile'"
    - or './gradlew build' should work, too
- run './gradlew copyRuntimeLibs' in microscenery
- copy microscenerys dependencies from the '\microsceneryDependencies' folder into '\dependencies\artifacts\compile'
- run 'ant jar'

#### Build with Idea

- copy "C:\Users\JanCasus\repos\micro-manager\dependencies\artifacts\compile\iconloader-GIT.jar" also into "'\dependencies\artifacts\compile'"
- install ant plugin
- create ant config targeting "jar"
- point IDE to "\dependencies\artifacts\compile" for libraries
- point IDE to "C:\Program Files\Micro-Manager-2.0"
- point IDE to "C:\Program Files\Micro-Manager-2.0\plugins\MicroManager"
- delete old protobuff dependency (2.x) from "\dependencies\artifacts\compile"

### Prepare a MicroManger Installation for the plugin

- build microscenery-core and copy "..\core\build\libs\microscenery-core-1.0-SNAPSHOT.jar" to "mm/plugins"
  - or './gradlew build' should work, too
- run './gradlew copyRuntimeLibs' in microscenery and copy '/microsceneryDependencies' to "mm/plugins"
- delete "mmCore.jar" from microsceneryDependencies
- delete "protobuf-java-.." from "mm\plugins\Micro-Manager"
- run 'ant jar' here
- copy the content from '\build\Java\plugins' to '..\Micro-Manager-2.0gamma\plugins\Micro-Manager' or the actual mmplugin folder.

### Logs 

- mm logs are in '..\Micro-Manager-2.0gamma\CoreLogs'