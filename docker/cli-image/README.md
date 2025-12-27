## Docker files

### Scripts

See the `../scripts` for some handy scripts. Especially, `build-image.sh` will load prerequisites into the right place.

### Single-platform builds

These are handy for building a local image to test the `runner` with, as a full multi-platform build is quite slow.

```bash
docker build --platform linux/arm64/v8 -t temperlang/temper:test .

docker build --platform linux/amd64 -t temperlang/temper:test .
```

### Multi-platform builds

[Many more details here](https://docs.docker.com/build/building/multi-platform/) but the short answer is you need to create and use a builder.

```bash
docker buildx create --name temperbuilder --driver docker-container \
	 --bootstrap --use

# For later invocations
docker buildx use --builder temperbuilder

docker buildx build --platform linux/amd64,linux/arm64/v8 -t temperlang/temper:test . --push
```
