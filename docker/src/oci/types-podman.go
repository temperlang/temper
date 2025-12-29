package oci

import (
	"github.com/containers/podman/v4/libpod/define"
	"github.com/containers/podman/v4/pkg/domain/entities"
)

type podContainer struct {
	entities.ListContainer
}

func (pc *podContainer) ContainerID() string {
	return pc.ID
}

type podContainerExecInfo struct {
	ies define.InspectExecSession
}

func (pcei *podContainerExecInfo) ContainerID() string {
	return pcei.ies.ContainerID
}

func (pcei *podContainerExecInfo) ExecID() string {
	return pcei.ies.ID
}

func (pcei *podContainerExecInfo) Pid() int {
	return pcei.ies.Pid
}

func (pcei *podContainerExecInfo) ExitCode() int {
	return pcei.ies.ExitCode
}

func (pcei *podContainerExecInfo) Running() bool {
	return pcei.ies.Running
}
