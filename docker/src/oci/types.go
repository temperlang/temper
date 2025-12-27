package oci

import "github.com/temperlang/temper-docker/common"

type ImageDetails struct {
	ImageName  string
	WorkingDir string
	EntryPoint []string
}

type ContainerDetails struct {
	EnsureMode    EnsureContainerMode
	ImageName     string
	ContainerName string
	MountContTgt  string
	MountHostSrc  string
	ContainerUser *common.UidGid
}

type EnsureContainerMode int

const (
	EnsureContainerLeastWork    EnsureContainerMode = iota
	EnsureContainerNewContainer                     // refresh the container
	EnsureContainerNeverPull                        // create a new container if needed, but never pull
)

type ContainerExecDetails struct {
	ContainerId string
	Command     []string
	Env         map[string]string
	WorkingDir  string
	User        string
}

// Use an interface so this can be lazy; if we need to
// look up details we can do that.
type ContainerInfo interface {
	ContainerID() string
}

type idOnly struct {
	ID string
}

func (c *idOnly) ContainerID() string {
	return c.ID
}

type ContainerExecInfo interface {
	ContainerID() string
	ExecID() string
	Pid() int
	ExitCode() int
	Running() bool
}

// These are for logging in to a container registry
type UserPass struct {
	UserName string
	Password string
}
