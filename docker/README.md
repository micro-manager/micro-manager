# Micro-Manager Docker Environment

This Docker setup provides a containerized environment to build and run Micro-Manager.

## Getting Started

The recommended way to run Micro-Manager is using Docker Compose, which handles the necessary configuration for hardware access (USB/Serial) and GUI display.

### Prerequisites

- Docker and Docker Compose installed.
- (Linux) X11 server running (local display).

### Running Micro-Manager

The easiest way to launch Micro-Manager is using the provided script at the project root:

```bash
./run-linux.sh
```

This script automatically configures the X11 display authority and uses `docker compose` to start the container.


## Proprietary Drivers

If your build requires proprietary drivers or SDKs for specific hardware adapters, you can include them without modifying the `Dockerfile`:

1. Create a setup script at `docker/setup-drivers.sh`.
2. Place your driver files in a local directory (e.g., `drivers/`).
3. In `setup-drivers.sh`, add commands to install the drivers, copy headers to `/usr/local/include`, and libraries to `/usr/local/lib`.

The build process will automatically detect and execute this script if it exists.

## Build Optimization Strategy

The Docker environment uses layer caching to optimize rebuilds:

1. **System dependencies** (rarely changes)
2. **ImageJ download** (rarely changes)
3. **Source copy** (changes with code edits)
4. **Proprietary Driver Hook** (optional)
5. **Micromanager Build** (runs on code changes)

## Directory Structure

- `/root/mm-src/micro-manager` - Micro-Manager source code
- `/root/ImageJ` - Built ImageJ installation with Micro-Manager plugin
- `/root/ImageJ/micromanager.sh` - Launch script

## Tips

- The container runs in `privileged` mode with `/dev` mounted to allow access to cameras and controllers.
- X11 socket and authority are shared with the host to enable the GUI.
- The project root is mounted to `/workdir` inside the container for easy access to data and logs.
