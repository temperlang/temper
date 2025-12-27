package oci

import (
	"context"

	docker "github.com/docker/docker/client"
)

// Hide the docker client in the context to behave like podman
var ociKey = struct{}{}

// Originally thought I'd store more in the context, but
// avoided that. This makes it clear this is all there is.
type ociContext struct {
	dkrClient *docker.Client
}

func newOciContext(ctx context.Context, dkrClient *docker.Client) context.Context {
	return context.WithValue(ctx, &ociKey, ociContext{dkrClient: dkrClient})
}

func dockerClient(ctx context.Context) *docker.Client {
	data, _ := ctx.Value(&ociKey).(ociContext)
	return data.dkrClient
}
