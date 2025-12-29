package oci

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"

	"github.com/containers/podman/v4/pkg/api/handlers"
	"github.com/containers/podman/v4/pkg/bindings"
	cont "github.com/containers/podman/v4/pkg/bindings/containers"
	img "github.com/containers/podman/v4/pkg/bindings/images"
	"github.com/containers/podman/v4/pkg/domain/entities"
	"github.com/containers/podman/v4/pkg/specgen"
	"github.com/moby/term"
	specs "github.com/opencontainers/runtime-spec/specs-go"
	log "github.com/sirupsen/logrus"
	"github.com/temperlang/temper-docker/common"
)

// Finds a container based on a unique specifier; name or id.
// field should be "name" or "id"
func podContainerFindBySpecifier(ctx context.Context, field string, val string) ([]entities.ListContainer, error) {
	// All: include non-running containres
	opts := cont.ListOptions{Filters: map[string][]string{field: {val}}, All: common.Ptr(true)}
	containers, err := cont.List(ctx, &opts)
	if err != nil {
		return nil, fmt.Errorf("while listing container: %w", err)
	}

	return containers, nil
}

// Use filters to match only containers with the correct name, then double-check in case docker gives
// us a prefix match.
func podContainerFindByName(ctx context.Context, name string) (ContainerInfo, error) {
	containers, err := podContainerFindBySpecifier(ctx, "name", name)
	if err != nil {
		return nil, fmt.Errorf("while getting container %s: %w; %v", name, err, ctx)
	}
	slashName := "/" + name
	for _, cnt := range containers {
		log.Debugf("Checking container %v", cnt.ID)
		for _, nm := range cnt.Names {
			log.Tracef("Checking name %v vs %v", nm, name)
			if nm == name || nm == slashName {
				return &podContainer{cnt}, nil
			}
		}
	}
	return nil, nil
}

// Given an image reference like 'ubuntu:latest' find the ID of the image
func podImageFindId(ctx context.Context, name string) (string, error) {

	images, err := img.List(ctx, &img.ListOptions{
		Filters: map[string][]string{"reference": {name}},
	})
	if err != nil {
		return "", fmt.Errorf("looking up image by reference: %w", err)
	}
	for _, img := range images {
		return img.ID, nil
	}
	return "", nil
}

func podImagePull(ctx context.Context, imageName string) (string, error) {
	pullOpts := img.PullOptions{}
	auth, err := podLoadAuth(imageName)
	if err == nil {
		pullOpts.Username = &auth.UserName
		pullOpts.Password = &auth.Password
	}

	images, err := img.Pull(ctx, imageName, &pullOpts)
	if len(images) > 0 {
		return images[0], nil
	}
	if err == nil {
		err = fmt.Errorf("pulling '%s' unepxectedly returned no image ID", imageName)
	}
	return "", err
}

func podContainerCreate(
	ctx context.Context,
	details ContainerDetails,
) (ContainerInfo, error) {
	// Set up the container create command.
	spec := specgen.SpecGenerator{}
	spec.Name = details.ContainerName
	spec.Image = details.ImageName
	spec.UserNS = specgen.Namespace{
		NSMode: specgen.KeepID,
		Value:  fmt.Sprintf("uid=%d,gid=%d", details.ContainerUser.UID, details.ContainerUser.GID),
	}
	if details.MountContTgt != "" && details.MountHostSrc != "" {
		// Should default to the mounted bind
		// We'll update to the correct WD when we exec
		spec.WorkDir = details.MountContTgt
		// many mount options are stringly typed
		spec.Mounts = append(spec.Mounts, specs.Mount{
			Type:        "bind",
			Destination: details.MountContTgt,
			Source:      details.MountHostSrc,
			Options:     []string{},
			// default options:
			// "readonly=false"
			// "bind-propagation=rprivate"
			// "bind-nonrecursive=false"
			// "consistency=default"
		})
	} else {
		log.Warn("container mount path was not set")
	}

	createResponse, err := cont.CreateWithSpec(ctx, &spec, nil)
	for _, warn := range createResponse.Warnings {
		log.Warnf("while creating container: %v", warn)
	}
	if err != nil {
		return nil, fmt.Errorf("creating container: %w", err)
	}
	err = cont.ContainerInit(ctx, createResponse.ID, nil)
	if err != nil {
		return nil, fmt.Errorf("initializing container: %w", err)
	}
	return &idOnly{createResponse.ID}, nil
}

func podContainerStart(ctx context.Context,
	id string,
) error {
	err := cont.Start(ctx, id, &cont.StartOptions{})
	if err != nil {
		return fmt.Errorf("starting container: %w", err)
	}
	return nil
}

func podContainerDelete(
	ctx context.Context,
	id string,
) error {
	err := cont.Stop(ctx, id, &cont.StopOptions{
		Timeout: common.Ptr(uint(3)),
		Ignore:  common.Ptr(true), // Don't complain if container already stopped
	})
	if err != nil {
		return err
	}
	return simplerRemove(ctx, id)
}

// Adapted from cont.Remove; this skips the reports because 3.x doesn't provide the correct JSON.
func simplerRemove(ctx context.Context, nameOrID string) error {
	options := &cont.RemoveOptions{
		Force:   common.Ptr(true),
		Timeout: common.Ptr(uint(3)),
		Ignore:  common.Ptr(true),
	}
	conn, err := bindings.GetClient(ctx)
	if err != nil {
		return err
	}
	params, err := options.ToParams()
	if err != nil {
		return err
	}
	response, err := conn.DoRequest(ctx, nil, http.MethodDelete, "/containers/%s", params, nil, nameOrID)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	io.Copy(io.Discard, response.Body)
	if response.StatusCode >= 400 {
		return errors.New(response.Status)
	}
	return nil
}

func podContainerExec(
	ctx context.Context,
	details ContainerExecDetails,
) (ContainerExecInfo, error) {
	ti := newTermInfo(ctx, details.Env)

	cfg := handlers.ExecCreateConfig{}
	cfg.Tty = ti.tty
	cfg.AttachStdin = true
	cfg.AttachStderr = !ti.tty
	cfg.AttachStdout = true
	cfg.Cmd = details.Command
	cfg.Env = common.EnvPairsFromMap(details.Env)
	cfg.WorkingDir = details.WorkingDir
	cfg.ConsoleSize = twoUint(&ti.size)
	cfg.User = details.User

	session, err := cont.ExecCreate(ctx,
		details.ContainerId,
		&cfg,
	)
	if err != nil {
		return nil, fmt.Errorf("creating container execution: %w", err)
	}

	stdin, stdout, stderr := term.StdStreams()

	opts := cont.ExecStartAndAttachOptions{
		InputStream:  bufio.NewReader(stdin),
		OutputStream: &stdout,
		ErrorStream:  &stderr,
		AttachInput:  common.Ptr(true),
		AttachOutput: common.Ptr(true),
		AttachError:  common.Ptr(!ti.tty),
	}

	err = cont.ExecStartAndAttach(ctx, session, &opts)
	if err != nil {
		return nil, fmt.Errorf("starting container execution: %w", err)
	}

	return podContainerExecInspect(ctx, session)
}

func podContainerExecInspect(
	ctx context.Context,
	session string,
) (ContainerExecInfo, error) {
	report, err := cont.ExecInspect(ctx, session, nil)
	if err != nil {
		return nil, err
	}
	if report == nil {
		return nil, fmt.Errorf("unexpectedly, ExecInspect did not return a report for %v", session)
	}
	return &podContainerExecInfo{*report}, nil
}
