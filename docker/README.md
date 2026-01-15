# Micro-Manager Docker Development Environment

This Docker setup is optimized for developing features in mmCoreAndDevices with fast iteration cycles.

## Building the Image

Build the Docker image from the project root:

```bash
docker build -t micro-manager-dev -f docker/Dockerfile .
```

**Note:** The initial build includes both Java and C++ components and may take considerable time.

## Running the Container

**To run Micro-Manager:**
```bash
docker run -it micro-manager-dev
```

**To rebuild C++ components:**
```bash
docker run -it \
  -v $(pwd)/mmCoreAndDevices:/home/engineer/mm-src/micro-manager/mmCoreAndDevices \
  micro-manager-dev \
  rebuild-cpp
```

**To run a custom command:**
```bash
docker run -it micro-manager-dev bash
```

## Development Workflow for mmCoreAndDevices

The Docker image has a flexible entrypoint that supports multiple use cases:

### Quick C++ rebuild (Fast - Recommended for iterative development)

After modifying mmCoreAndDevices files:

```bash
docker run --rm \
  -v $(pwd)/mmCoreAndDevices:/home/engineer/mm-src/micro-manager/mmCoreAndDevices \
  micro-manager-dev \
  rebuild-cpp
```

Or use the convenience script:
```bash
./build-mmcore.sh
```

This rebuilds only C++ components without Java compilation (much faster).

### Interactive development

For an interactive shell to work with the code:

```bash
docker run -it \
  -v $(pwd)/mmCoreAndDevices:/home/engineer/mm-src/micro-manager/mmCoreAndDevices \
  micro-manager-dev \
  bash
```

Then inside the container:
- `/home/engineer/rebuild-cpp.sh` - Rebuild C++ only
- Manual build commands as needed

### Manual rebuild

Inside the container:

```bash
cd /home/engineer/mm-src/micro-manager
make clean
./configure --without-java --enable-imagej-plugin=/home/engineer/ImageJ
make -j8
make install
```

## Build Optimization Strategy

The Dockerfile uses layer caching to optimize rebuilds:

1. **PVCAM installation** (rarely changes)
2. **System dependencies** (rarely changes)
3. **ImageJ download** (rarely changes)
4. **Source copy** (changes with code)
5. **Full build with Java** (initial build only)

When you rebuild the image after source changes, Docker will use cached layers for steps 1-3, only rebuilding from step 4 onwards.

For iterative development within a running container, use the rebuild script to avoid Java recompilation entirely.

## Directory Structure

- `/home/engineer/mm-src/micro-manager` - Source code
- `/home/engineer/ImageJ` - ImageJ installation with Micro-Manager plugin
- `/home/engineer/rebuild-cpp.sh` - Quick rebuild script for C++ only
- `/home/engineer/entrypoint.sh` - Flexible entrypoint script

## Tips

- Mount your local mmCoreAndDevices as a volume for live development
- Use `rebuild-cpp` argument for fast C++ iterations
- Run without arguments to start Micro-Manager
- The Java components are built once during image creation
- Only rebuild the full image when dependencies or system setup changes
