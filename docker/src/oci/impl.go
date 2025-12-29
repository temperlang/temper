package oci

import (
	"context"
	"fmt"

	log "github.com/sirupsen/logrus"
)

// This builds on the per-client commands to do the same basic dance on
// docker and podman:
// - look up a container and return it (happy case)
// - drop an existing container if the user asks us to
// - look up the image if it's available (still fairly happy case)
// - pull the image if needed (sad case)
// - create the container
func EnsureContainer(ctx context.Context, cont ContainerDetails) (ContainerInfo, error) {
	container, err := ContainerFindByName(ctx, cont.ContainerName)
	if err != nil {
		log.Warnf("finding %s but got: %v", cont.ContainerName, err)
		return nil, err
	}
	if container != nil {
		if cont.EnsureMode == EnsureContainerLeastWork {
			log.Debugf("found existing container %s", container.ContainerID())
			return container, nil
		}
		// We need to drop the existing container
		err = ContainerDelete(ctx, container.ContainerID())
		if err != nil {
			return nil, fmt.Errorf("removing existing container: %w", err)
		}
	}
	container = nil
	log.Debugf("look for image %s", cont.ImageName)
	imageId, err := ImageFindId(ctx, cont.ImageName)
	if err != nil {
		return nil, fmt.Errorf("finding image ID: %w", err)
	}
	if imageId == "" {
		if cont.EnsureMode == EnsureContainerNeverPull {
			return nil, fmt.Errorf("image '%s' not present and --no-pull specified", cont.ImageName)
		}
		log.Infof("pulling image '%s'", cont.ImageName)
		imageId, err = ImagePull(ctx, cont.ImageName)
		if err != nil {
			return nil, fmt.Errorf("pulling image: %w", err)
		}
	}
	if imageId == "" {
		return nil, fmt.Errorf("failed to pull image: %s", cont.ImageName)
	}

	log.Debugf("creating container %s", cont.ContainerName)
	container, err = ContainerCreate(ctx, cont)
	if err != nil {
		return nil, err
	}
	log.Tracef("new container is %v", container)
	if container == nil {
		return nil, fmt.Errorf("failed to look up created container")
	}
	return container, err
}
