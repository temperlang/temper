package common

import (
	"errors"
	"io/fs"
	"os"
	"path/filepath"
)

// The name of the library configuration file.
// See lang.temper.library.LibraryConfiguration.Companion#getFileName
const ConfigTemperMd = "config.temper.md"
const src = "src"

var ErrNoConfigFound = errors.New("no config.temper.md found to mark root")

type TemperLibraryConfig struct {
	LibraryRoot        string
	WorkingDirRelative string
}

// Searches for the library root starting at the path (usually os.Getwd())
// and looking parent by parent.
// See lang.temper.library.LibraryConfigurationKt#findRoot
// Returns the library root, and the original path relative to the root.
func FindTemperLibraryRootFrom(path string) (TemperLibraryConfig, error) {
	search := path
	tlc := TemperLibraryConfig{path, filepath.Base("")}
	var up string
	for ; ; search = up {
		if up = filepath.Dir(search); up == search {
			break
		}
		candidates := []string{
			filepath.Join(search, src, ConfigTemperMd),
			filepath.Join(search, ConfigTemperMd),
		}
		for _, candidate := range candidates {
			stat, err := os.Stat(candidate)
			if err != nil {
				if !errors.Is(err, fs.ErrNotExist) {
					return tlc, err
				}
				continue
			}
			if !stat.IsDir() {
				wd, err := filepath.Rel(search, path)
				if err != nil {
					return tlc, err
				}
				tlc.LibraryRoot = search
				tlc.WorkingDirRelative = wd
				return tlc, nil
			}
		}
	}
	// If there's no parent path, we're in the root,
	// and should never try to mount root.
	return tlc, ErrNoConfigFound
}
