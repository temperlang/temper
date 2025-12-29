# Launcher

## Build

See `build.sh` for more details.

Generally, test builds are simply:

```sh
./build.sh launcher image
```

That will bundle the launcher into a local image.

## Usage

The launcher is set as the entrypoint for the image, so usage is transparent. At least, that's the theory. The launcher logs to stderr, so its progress can be monitored by looking at the container logs.

The launcher has two modes:

### Init process

The launcher will monitor the `.active-pids` file, and when no processes listed are running, it will wait a few minutes and shut down.

### Exec process

When given args, the launcher will add the current process ID to `.active-pids` and then exec the process.