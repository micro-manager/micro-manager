# Micro-Manager Docker Environment

This Docker setup provides a containerized environment to build and run Micro-Manager.

## Getting Started

The recommended way to run Micro-Manager in Docker is using Docker Compose, which handles the necessary configuration for hardware access (USB/Serial) and GUI display.

### Prerequisites

- Docker and Docker Compose installed.
- (Linux) X11 server running (local display). If you are using Wayland, the GUI will typically run via XWayland.

### Running Micro-Manager

The easiest way to launch Micro-Manager is using the provided script at the project root:

```bash
./run-linux.sh
```

This script automatically configures the X11 display authority and uses `docker compose` to start the container.


## Proprietary Drivers

If your build requires proprietary drivers or SDKs for specific hardware adapters, you can include them without modifying the `Dockerfile`:

1. Create a setup script at `docker/setup-drivers.sh`.
2. Place your driver files in the `docker/drivers/` directory (which is ignored by git).
3. In `setup-drivers.sh`, add commands to install the drivers, copy headers to `/usr/local/include`, and libraries to `/usr/local/lib`.

The build process will automatically detect and execute this script if it exists.

Note: the included `setup-drivers.sh` contains a build script for PVCAM drivers. If the PVCAM driver zip is present it will be installed.

## Build Optimization Strategy

The Docker environment uses layer caching to optimize rebuilds:

1. **System dependencies** (rarely changes)
2. **ImageJ download** (rarely changes)
3. **Source copy** (changes with code edits)
4. **Proprietary Driver Hook** (optional)
5. **Micromanager Build** (runs on code changes)

## Directory Structure

- `/root/mm-src/micro-manager` - Micro-Manager source code
- `/opt/ImageJ` - Built ImageJ installation with Micro-Manager plugin
- `/opt/ImageJ/micromanager.sh` - Launch script
- `/data` - Default directory for saved images (mapped to `~/MicroManagerData` on host)
- `/configs` - Directory for hardware configuration files (mapped to `./configs` on host)
- `/root/.config/Micro-Manager` - User profile and persistent settings (mapped to `./profiles` on host)

## Tips

- The container runs in `privileged` mode with `/dev` mounted to allow access to cameras and controllers.
- X11 socket and authority are shared with the host to enable the GUI.
- The project root is mounted to `/workdir` inside the container for easy access to data and logs.
- The default working directory is `/workdir`, so any files saved will appear in your project root on the host.
- Hardware configurations can be placed in the `./configs` directory on your host and accessed via `/configs` inside the container.
- User profiles, including the recently used configuration file, are persisted in the `./profiles` directory on your host.
- Acquired images are saved to `~/MicroManagerData` by default. You can change this by setting the `MM_DATA_DIR` environment variable on your host.
