// go:build !windows
package oci

import (
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"

	log "github.com/sirupsen/logrus"
	"github.com/temperlang/temper-docker/common"
	"github.com/temperlang/temper-docker/xdg"
)

const npipeBase = `\\.\pipe\`

// On Linux, expect podman to be listening at
// $XDG_RUNTIME_DIR/podman/podman.sock
// On Darwin, it lives in:
// $XDG_DATA_HOME/containers/podman/machine/*/podman.sock
// On Windows, pipes live under \\.\pipe\
func podmanSockets() []string {
	paths := make([]string, 0, 8)
	var transport string

	if runtime.GOOS == "windows" {
		transport = "npipe://"
		entries, err := os.ReadDir(npipeBase)
		if err == nil {
			for _, ent := range entries {
				name := ent.Name()
				if strings.Contains(name, "podman") {
					paths = append(paths, filepath.Join(npipeBase, name))
				}
			}
		} else {
			log.Debug("Unable to read named pipes; falling back to default name.")
			paths = append(paths, filepath.Join(npipeBase, `podman-machine-default`))
		}
	} else {
		transport = "unix://"
		cand, err := xdg.RuntimeHome("podman/podman.sock")
		if err == nil {
			paths = append(paths, cand)
		}

		cand, err = xdg.DataHome("containers/podman/machine")
		if err == nil {
			fmt.Fprintf(os.Stderr, "Reading under: %v\n", cand)
			entries, err := os.ReadDir(cand)
			if err == nil {
				for _, ent := range entries {
					if ent.IsDir() {
						paths = append(paths, filepath.Join(cand, ent.Name(), "podman.sock"))
					}
				}
			}
		}

		// Lastly, try a root podman socket
		paths = append(paths, filepath.FromSlash("/run/podman/podman.sock"))
	}

	existing := make([]string, 0, len(paths))
	for _, sock := range paths {
		if common.Exists(sock) {
			existing = append(existing, transport+filepath.ToSlash(sock))
		}
	}

	return existing
}
