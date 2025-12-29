package common

import (
	ref "github.com/docker/distribution/reference"
)

type ImageConfig struct {
	imageRef           ref.NamedTagged
	LauncherPath       []string
	MainCmd            []string
	ShellCmd           []string
	MountPath          string
	ImageEnv           map[string]string
	ContainerUser      *UidGid
	ContainerUserName  string
	ContainerAdminName string
}

type UidGid struct {
	UID int
	GID int
}

// The bundle lines are parsed by the build script; we're bundling
// the docker image, launcher and runner together.
var bundleRef = "ghcr.io/temperlang/temper:cli-dev"

func BundleRef() string {
	return bundleRef
}

func makeKnownRef(str string) ref.NamedTagged {
	parsed, err := ref.ParseNamed(str)
	if err != nil {
		panic(err)
	}
	nt, ok := parsed.(ref.NamedTagged)
	if !ok {
		panic("refs in imageconfig must be tagged")
	}
	return nt
}

var ImageConfigs = []ImageConfig{
	{
		imageRef:           makeKnownRef(BundleRef()),
		LauncherPath:       []string{"/home/temper/.local/bin/launcher"},
		MainCmd:            []string{"/home/temper/.local/bin/temper"},
		ShellCmd:           []string{"/bin/bash"},
		MountPath:          "/home/temper/work",
		ImageEnv:           map[string]string{},
		ContainerUser:      &UidGid{UID: 1000, GID: 1000},
		ContainerUserName:  "temper",
		ContainerAdminName: "root",
	},
}

// OCI standard
const defaultTag = "latest"

// For matching, just look for the correct repository.
func ParseRefAndSimplify(name string) (string, error) {
	parsed, err := ref.ParseNamed(name)
	if err != nil {
		return name, err
	}
	path := ref.Path(parsed)
	tag := ""
	nt, ok := parsed.(ref.NamedTagged)
	if ok {
		tag = nt.Tag()
	} else {
		tag = defaultTag
	}
	return path + ":" + tag, nil
}

// Drops the Domain part of the image.
func (cfg ImageConfig) RefWithoutDomain() string {
	path := ref.Path(cfg.imageRef)
	tag := cfg.imageRef.Tag()
	return path + ":" + tag
}

// Drops the Domain part of the image.
func (cfg ImageConfig) FullRef() string {
	return cfg.imageRef.String()
}
