## Micro-Manager Microscenery plugin

For orginal readme see parent repo.

### How to build

- do the prequisites of [https://micro-manager.org/Writing_plugins_for_Micro-Manager]
- build microscenery-core and copy "..\core\build\libs\microscenery-core-1.0-SNAPSHOT.jar" to "'\dependencies\artifacts\compile'"
    - or './gradlew build' should work, too
- run './gradlew copyRuntimeLibs' in microscenery
- copy microscenerys dependencies from the '\microsceneryDependencies' folder into '\dependencies\artifacts\compile'
- run 'ant jar'

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