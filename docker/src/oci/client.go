package oci

import (
	"context"
	"errors"
	"fmt"
	"os"

	semver "github.com/blang/semver/v4"
	podman "github.com/containers/podman/v4/pkg/bindings"
	podversion "github.com/containers/podman/v4/version"
	"github.com/docker/docker/api/types"
	docker "github.com/docker/docker/client"
	log "github.com/sirupsen/logrus"
)

// Construct a client and set it in the context
func NewClient(ctx context.Context) (context.Context, error) {
	// Allow Podman to connect to 3.x
	overridePodmanVersion()
	// If podman is specified in the environment.
	hostUri := os.Getenv("CONTAINER_HOST")
	if hostUri != "" {
		log.Debugf("using podman at %s", hostUri)
		podCtx, err := podman.NewConnection(ctx, hostUri)
		if err != nil {
			return ctx, err
		}
		return newOciContext(podCtx, nil), nil
	}

	errs := make([]error, 0, 4)
	// Docker will check if it's specified in the environment.
	dockerClient, err := docker.NewClientWithOpts(docker.FromEnv, docker.WithAPIVersionNegotiation())

	// Check if docker is in fact podman.
	if err == nil {
		info, dockerErr := dockerClient.ServerVersion(ctx)
		if dockerErr == nil {
			hostUri = dockerClient.DaemonHost()
			if isPodman(info) {
				log.Debugf("using podman %s", hostUri)
				// Oops, we're running podman, try using the podman client
				podCtx, err := podman.NewConnection(ctx, hostUri)
				if err == nil {
					// We've got a valid podman connection, so close out docker.
					if err = dockerClient.Close(); err != nil {
						log.Warnf("Failed to close replaced client: %v", err)
					}
					return newOciContext(podCtx, nil), nil
				}
			}
			log.Debugf("using docker %s", hostUri)
			return newOciContext(ctx, dockerClient), nil
		} else {
			errs = append(errs, dockerErr)
		}
	} else {
		errs = append(errs, fmt.Errorf("try docker: %w", err))
	}

	// Fallback scan for podman.
	for _, sock := range podmanSockets() {
		log.Debugf("checking %s", sock)
		podCtx, err := podman.NewConnection(ctx, sock)
		if err == nil {
			log.Debugf("using podman %s", sock)
			return newOciContext(podCtx, nil), nil
		} else {
			errs = append(errs, fmt.Errorf("try podman @ %s: %w", sock, err))
		}
	}
	return ctx, errors.Join(errs...)
}

func isPodman(info types.Version) bool {
	for _, comp := range info.Components {
		if comp.Name == "Podman Engine" {
			return true
		}
	}
	return false
}

func overridePodmanVersion() {
	podversion.APIVersion[podversion.Libpod][podversion.MinimalAPI] = semver.MustParse("3.0.0")
}
