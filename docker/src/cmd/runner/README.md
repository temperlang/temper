# Runner

## Build

See `build.sh` to build binaries for multiple platforms.

Generally, test builds are simply:

```sh
./build.sh runner
```

## Usage

Ideally, the runner should be a thin layer over the Temper CLI. If it's named `temper` and in your path, it should behave as though you invoked `temper`.

For command help, run `temper --runner-help`. A regular `--help` or `help` will remind you of this before
passing control to the Temper help command.

## Container control

Temper containers are named based on the project root, e.g. a project in `/home/bob/work` would have the container name `temper_home_bob_work`.

If no process is running within the container, it will stop after a few minutes. The launcher will automatically restart it as needed.

Containers can be stopped manually and deleted normally. Containers naturally cache dependencies, so deleting a container is a simple way to clear that cache.
