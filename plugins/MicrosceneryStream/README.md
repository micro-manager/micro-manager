## Micro-Manager Microscenerz plugin

For orginal readme see parent repo.

### How to run

- do the prequisites of [https://micro-manager.org/Writing_plugins_for_Micro-Manager]
- replace ../Micro-Manager-2.0gamma/jre with a more recent java version (eg. 11)
- run './gradlew copyRuntimeLibs' in microscenery
- copy microscenerys dependencies from the '/microsceneryDependencies' folder into '\dependencies\artifacts\compile' and '..\Micro-Manager-2.0gamma\plugins\Micro-Manager'
- run 'ant jar'
- copy the content from '\build\Java\plugins' to '..\Micro-Manager-2.0gamma\plugins\Micro-Manager' or the actual mmplugin folder.
- remove the old kryo version, mm brings

- check for logs in '..\Micro-Manager-2.0gamma\CoreLogs'

