package oci

import (
	"context"
	"fmt"
	"io"

	"github.com/docker/docker/api/types"
	cont "github.com/docker/docker/api/types/container"
	"github.com/docker/docker/api/types/filters"
	"github.com/docker/docker/api/types/mount"
	docker "github.com/docker/docker/client"
	"github.com/docker/docker/errdefs"
	"github.com/moby/term"
	log "github.com/sirupsen/logrus"
	"github.com/temperlang/temper-docker/common"
)

// Finds a container based on a unique specifier; name or id.
// field should be "name" or "id"
func dkrContainerFindBySpecifier(ctx context.Context, client *docker.Client, field string, val string) ([]types.Container, error) {
	filter := filters.NewArgs(filters.Arg(field, val))
	// All: include non-running containres
	opts := types.ContainerListOptions{All: true, Filters: filter}
	containers, err := client.ContainerList(ctx, opts)
	if err != nil {
		return nil, fmt.Errorf("(dkr)while listing container: %w", err)
	}
	return containers, nil
}

// Use filters to match only containers with the correct name, then double-check in case docker gives
// us a prefix match.
func dkrContainerFindByName(ctx context.Context, client *docker.Client, name string) (ContainerInfo, error) {
	containers, err := dkrContainerFindBySpecifier(ctx, client, "name", name)
	if err != nil {
		return nil, err
	}
	slashName := "/" + name
	for _, cnt := range containers {
		for _, nm := range cnt.Names {
			if nm == name || nm == slashName {
				return &dkrContainer{cnt}, nil
			}
		}
	}
	return nil, nil
}

// Given an image reference like 'ubuntu:latest' find the ID of the image
func dkrImageFindId(ctx context.Context, client *docker.Client, name string) (string, error) {
	args := filters.NewArgs(filters.Arg("reference", name))
	opts := types.ImageListOptions{Filters: args}

	images, err := client.ImageList(ctx, opts)
	if err != nil {
		return "", fmt.Errorf("looking up image by reference: %w", err)
	}
	for _, img := range images {
		return img.ID, nil
	}
	return "", nil
}

func dkrImagePull(ctx context.Context, client *docker.Client, imageName string) (string, error) {
	opts := types.ImagePullOptions{}
	authString, authErr := dkrLoadAuth(imageName)
	if authErr == nil {
		opts.RegistryAuth = authString
	} else {
		log.Tracef("No auth found to pull %s", imageName)
	}

	reader, err := client.ImagePull(ctx, imageName, opts)
	if err != nil {
		return "", fmt.Errorf("starting image pull: %w; auth error: %w", err, authErr)
	}
	defer reader.Close()
	_, err = io.Copy(io.Discard, reader)
	if err != nil {
		return "", fmt.Errorf("during image pull: %w", err)
	}

	return dkrImageFindId(ctx, client, imageName)
}

func dkrContainerCreate(
	ctx context.Context,
	client *docker.Client,
	details ContainerDetails,
) (ContainerInfo, error) {
	// Set up the container create command.
	contConfig := &cont.Config{
		Image: details.ImageName,
	}
	var contHostConfig *cont.HostConfig
	if details.MountContTgt != "" && details.MountHostSrc != "" {

		// Should default to the mounted bind
		// We'll update to the correct WD when we exec
		contConfig.WorkingDir = details.MountContTgt
		contHostConfig = &cont.HostConfig{
			Mounts: []mount.Mount{
				{
					Type:        mount.TypeBind,
					Source:      details.MountHostSrc,
					Target:      details.MountContTgt,
					ReadOnly:    false,                    // default
					Consistency: mount.ConsistencyDefault, // default
					BindOptions: &mount.BindOptions{
						Propagation:      mount.PropagationRPrivate, // default
						NonRecursive:     false,                     // default
						CreateMountpoint: false,                     // default
					},
				},
			},
		}
	} else {
		log.Warn("container mount path was not set")
	}

	createResponse, err := client.ContainerCreate(
		ctx,
		contConfig,
		contHostConfig,
		nil,
		nil,
		details.ContainerName,
	)
	if err != nil {
		return nil, fmt.Errorf("creating container: %w", err)
	}
	return &idOnly{createResponse.ID}, nil
}

func dkrContainerStart(
	ctx context.Context,
	client *docker.Client,
	id string,
) error {
	return client.ContainerStart(ctx, id, types.ContainerStartOptions{})
}

func dkrContainerDelete(
	ctx context.Context,
	client *docker.Client,
	id string,
) error {
	err := client.ContainerStop(ctx, id, cont.StopOptions{Timeout: common.Ptr(3)})
	if err != nil {
		if _, ok := err.(errdefs.ErrNotModified); !ok {
			return err
		}
	}
	err = client.ContainerRemove(ctx, id, types.ContainerRemoveOptions{Force: true})
	return err
}

func dkrContainerExecResize(ctx context.Context, client *docker.Client, execId string, size term.Winsize) error {
	return client.ContainerExecResize(ctx, execId, types.ResizeOptions{
		Height: uint(size.Height),
		Width:  uint(size.Width),
	})
}

func dkrContainerExecInspect(ctx context.Context, client *docker.Client, execID string) (ContainerExecInfo, error) {
	inspect, err := client.ContainerExecInspect(ctx, execID)
	if err != nil {
		return nil, err
	}
	return &dkrContainerExecInspectInfo{inspect}, nil
}
