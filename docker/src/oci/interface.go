package oci

import (
	"context"
)

func ContainerCreate(
	ctx context.Context,
	details ContainerDetails,
) (ContainerInfo, error) {
	dkr := dockerClient(ctx)
	if dkr != nil {
		return dkrContainerCreate(ctx, dkr, details)
	} else {
		return podContainerCreate(ctx, details)
	}
}

func ContainerFindByName(
	ctx context.Context,
	name string,
) (ContainerInfo, error) {
	dkr := dockerClient(ctx)
	if dkr != nil {
		return dkrContainerFindByName(ctx, dkr, name)
	} else {
		return podContainerFindByName(ctx, name)
	}
}

// Gracefully stops and removes a container
func ContainerDelete(ctx context.Context,
	id string,
) error {
	dkr := dockerClient(ctx)
	if dkr != nil {
		return dkrContainerDelete(ctx, dkr, id)
	} else {
		return podContainerDelete(ctx, id)
	}
}

func ImageFindId(
	ctx context.Context,
	name string,
) (string, error) {
	dkr := dockerClient(ctx)
	if dkr != nil {
		return dkrImageFindId(ctx, dkr, name)
	} else {
		return podImageFindId(ctx, name)
	}
}

func ImagePull(ctx context.Context, imageName string) (string, error) {
	dkr := dockerClient(ctx)
	if dkr != nil {
		return dkrImagePull(ctx, dkr, imageName)
	} else {
		return podImagePull(ctx, imageName)
	}
}

func ContainerStart(ctx context.Context, id string) error {
	dkr := dockerClient(ctx)
	if dkr != nil {
		return dkrContainerStart(ctx, dkr, id)
	} else {
		return podContainerStart(ctx, id)
	}
}

func ContainerExec(ctx context.Context, details ContainerExecDetails) (ContainerExecInfo, error) {
	dkr := dockerClient(ctx)
	if dkr != nil {
		return dkrContainerExec(ctx, dkr, details)
	} else {
		return podContainerExec(ctx, details)
	}
}

func ContainerExecInspect(ctx context.Context, execId string) (ContainerExecInfo, error) {
	dkr := dockerClient(ctx)
	if dkr != nil {
		return dkrContainerExecInspect(ctx, dkr, execId)
	} else {
		return podContainerExecInspect(ctx, execId)
	}
}
