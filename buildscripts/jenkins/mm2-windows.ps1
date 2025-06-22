$ErrorActionPreference = 'Stop'
trap {
    Write-Error $_
    break
}

$Env:JAVA_HOME = $Env:JAVA8_HOME
$Env:Path = "$Env:JAVA_HOME\bin;$Env:Path"
if (-not $Env:GIT_INSTALL_ROOT) {
    $Env:GIT_INSTALL_ROOT = 'C:\Program Files\Git'
}
New-Item -ItemType Directory -Force -Path $HOME\.ssh
Copy-Item $Env:KNOWN_HOSTS_GITHUB "$HOME\.ssh\known_hosts"
$ivy2_cache_zip = 'ivy2-cache.zip'

Write-Output "JAVA_HOME = $Env:JAVA_HOME"
Write-Output "GIT_INSTALL_ROOT = $Env:GIT_INSTALL_ROOT"
Write-Output "Path = $Env:Path"
java -version
ant -version
  
Set-Location micro-manager
if ($Env:JOB_BASE_NAME -like '*-git-*') {
    git rev-parse --short HEAD | Out-File -Encoding ASCII 'version.txt'
    if ($LastExitCode -ne 0) { exit 1 }
}
Write-Output "version.txt set to $(Get-Content 'version.txt')"
Set-Location $Env:WORKSPACE

$rev_3ppublic = (Get-Content 'micro-manager\3rdpartypublic-revision')
Write-Output "Preparing 3rdpartypublic r$rev_3ppublic..."
Move-Item C:\3rdpartypublic .\
Set-Location 3rdpartypublic
svn update -q "-r$rev_3ppublic"
if ($LastExitCode -ne 0) { exit 1 }
Set-Location $Env:WORKSPACE

$rev_3p = (Get-Content 'micro-manager\3rdparty-revision')
Write-Output "Preparing 3rdparty r$rev_3p..."
Move-Item C:\3rdparty .\
Set-Location 3rdparty
svn --username $Env:SVN_USERNAME --password $Env:SVN_PASSWORD `
    update -q "-r$rev_3p"
if ($LastExitCode -ne 0) { exit 1 }
Set-Location $Env:WORKSPACE

Write-Output "Preparing SecretDeviceAdapters..."
Set-Location 'micro-manager\mmCoreAndDevices'
Copy-Item -Path $Env:MM_SECRETDEVICEADAPTERS_SSHKEY `
    -Destination $HOME\.ssh\id_ed25519
& "$Env:GIT_INSTALL_ROOT\bin\bash.exe" `
    'secret-device-adapters-checkout.sh' use_ssh
if ($LastExitCode -ne 0) { exit 1 }
Remove-Item -Path $HOME\.ssh\id_ed25519
Set-Location $Env:WORKSPACE

Write-Output "Restoring ivy2 cache if available..."
if (Test-Path $ivy2_cache_zip) {
    7z x -y $ivy2_cache_zip "-o$HOME"
    if ($LastExitCode -ne 0) { exit 1 }
    Remove-Item $ivy2_cache_zip
} else {
    Write-Output "$ivy2_cache_zip not found"
}

Write-Output "Running build..."
Set-Location 'micro-manager'
ant -f buildscripts\fetchdeps.xml "-Dmm.ivy.failonerror=true"
if ($LastExitCode -ne 0) { exit 1 }
ant -f buildscripts\nightly\nightlybuild_Windows.xml `
    -listener "org.apache.tools.ant.XmlLogger" `
    -logger "org.apache.tools.ant.listener.SimpleBigProjectLogger" `
    "-DXmlLogger.file=$Env:WORKSPACE\buildlog.xml" `
    "-Dmm.build.failonerror=true"
$result = $LastExitCode
Set-Location $Env:WORKSPACE

Write-Output "Generating build report..."
python 'micro-manager\buildscripts\nightly\genreport_Windows.py' `
    'micro-manager' buildlog.xml buildreport.html

Write-Output "Archiving ivy2 cache..."
7z a $ivy2_cache_zip $HOME\.ivy2

exit $result
