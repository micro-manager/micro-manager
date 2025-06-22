# Jenkins scripts for automated builds

These scripts are not (currently) actually invoked by the Jenkins builds, but
they are backups of the scripts being used. (This is because the testing loop
would be too cumbersome if we had to commit to the `main` branch each time we
change the scripts, at least under the current setup.)

There are currently 4 Jenkins jobs:

- `mm2-git-main-macos` (triggered on every push to `main`)
- `mm2-git-main-windows` (ditto)
- `mm2-nightly-macos` (triggered at 06:00 UTC if there has been a push)
- `mm2-nightly-windows` (ditto)

(Release build jobs may be added in the future.)

The job name (`$JOB_BASE_NAME` provided by Jenkins) is used to conditionalize
so that the common scripts `mm2-macos.sh` and `mm2-windows.ps1` can be used for
all 4 jobs. (For the Windows builds we use the Jenkins PowerShell plugin.)

Note that Windows builds run on single-use cloud VMs, whereas macOS builds run
on a bare-metal Mac mini. For macOS, we prevent multiple builds from running
concurrently, because we use shared resources on the machine (namely the SVN
working copies, which would be too expensive to check out from scratch on every
build). Also, we need to be mindful that parts of the environment will persist
between builds (e.g., tools installed by `uv`).
