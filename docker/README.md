# temper-docker

This repository contains images and utilities for managing the Temper toolchain via docker.

This is a utility to run the Temper CLI in an image that is preloaded with all the development tools recommended for various Temper backends.

## Getting started

To quickly get up and running with Temper, you'll need:

* Docker or a compatible container system
	* Podman is supported in rootless mode
* The `temper` runner utility

The Temper runner is a static binary, so you can simply install it anywhere you like in your path. It will be in the _releases_ tab of this repository.

Try this to get started:

```bash
temper help
```

On the first run, the `temper` runner will pull a docker image. The image contains compilers for all the target languages.

For details on the runner utility:

```bash
temper --runner-help
```

### Uninstalling

Uninstallation should be straightforward:

1. Delete the `temper` binary
2. Drop the Docker containers named `temper_*`
3. Drop the Docker images named `temperlang/temper:*`

## Repository contents

* `build.sh` - the build scripts for various components
* `image` - the Dockerfile to build the Temper CLI image
* `src` - the main Go module with various subpackages
	* `cmd` - contains separate modules for the launcher and runner
		* The commands are `main` packages
	* `oci` - abstracts over Docker and Podman
	* `launcher` - the body of the launcher command
	* `runner` - the body of the runner command
	* `stub` - contains a module that is stubbed out to avoid `cgo`

### Build products

There are three components to the Temper docker utility.

1. The docker image itself, which is built by the `base-image/` and `cli-image/` subdirectories.
   * The Temper CLI itself is installed
   * It contains recommended versions of build tools relevant to Temper users
   * The launcher tool is set as its ENTRYPOINT
2. The launcher tool, which is built by `src/cmd/launcher`
  * `./gradlew docker:launcher*` builds a launcher variant
   * This is responsible for launching the Temper CLI
   * It will also allow a running container to stop when it's not in use
   * It handles some internal logging
3. The runner tool, which is built by `src/cmd/runner`
   * `./gradlew docker:runnerLocal` will build for your system
   * `./gradlew docker:runnerAll` will build all variants
   * This is installed by the end-user on the target machine
   * It emulates the standard `docker run` command
   * It tries to reuse an existing container when possible
   * It automates the correct mapping to work within the current directory
   * The standard Temper docker image and configuration of the Temper CLI is baked in

#### Assembling the docker image

To keep build times reasonable, we build a custom base image with the backend tools, and then copy the Temper CLI into
that. The process is a bit involved, but generally the gradle tasks simplify it.

The gradle tasks don't attempt to authenticate to `ghcr.io/temperlang`.

* Base image
  * `docker:baseImageBuild*` will build a local base image for e.g. AMD64
  * `docker:baseImagePush*` build and push the image
  * These are run periodically only when we need to update backend tools.
    * See `base-image/home/.tool-versions`
* Cli image
  * `docker:cliImageBuild*` adds the Temper CLI and launcher to the base image
  * `docker:cliImagePush*` builds and pushes the image
  * `docker:cliImageMultiplatformCreate`
    * runs the push for all platforms
    * pushes the final multiplatform image
