package oci

import (
	"github.com/docker/docker/api/types"
	"github.com/moby/term"
)

func twoUint(size *term.Winsize) *[2]uint {
	if size == nil {
		return nil
	}
	return &[2]uint{uint(size.Height), uint(size.Width)}
}

type dkrContainer struct {
	types.Container
}

func (dc *dkrContainer) ContainerID() string {
	return dc.ID
}

type dkrContainerExecInspectInfo struct {
	cei types.ContainerExecInspect
}

func (dei *dkrContainerExecInspectInfo) ContainerID() string {
	return dei.cei.ContainerID
}

func (dei *dkrContainerExecInspectInfo) ExecID() string {
	return dei.cei.ExecID
}

func (dei *dkrContainerExecInspectInfo) Pid() int {
	return dei.cei.Pid
}

func (dei *dkrContainerExecInspectInfo) Running() bool {
	return dei.cei.Running
}

func (dei *dkrContainerExecInspectInfo) ExitCode() int {
	return dei.cei.ExitCode
}
