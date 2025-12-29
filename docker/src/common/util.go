package common

import (
	"encoding/json"
	"errors"
	"io"
	"os"
	"os/exec"
	"path"
)

// Concatenate a list of slices
func Concat[S ~[]E, E any](sx ...S) S {
	var (
		nonNil bool
		length int
	)
	for _, s := range sx {
		nonNil = nonNil || s != nil
		length += len(s)
	}
	if !nonNil {
		return nil
	}
	cx := make(S, 0, length)
	for _, s := range sx {
		cx = append(cx, s...)
	}
	return cx
}

// Convert our map back to Go's preferred model.
func EnvPairsFromMap(env map[string]string) []string {
	pairs := make([]string, 0, len(env))
	for key, val := range env {
		pairs = append(pairs, key+"="+val)
	}
	return pairs
}

// Some podman arguments require a pointer to a literal.
func Ptr[T any](v T) *T {
	return &v
}

// Tests if it's a root generic (Unixy) path
func IsRootGenericPath(pathname string) bool {
	return path.Dir(pathname) == pathname
}

func Exists(path string) bool {
	_, err := os.Stat(path)
	return !errors.Is(err, os.ErrNotExist)
}

func RunCommandWithStdin(stdinText string, cmdName string, args ...string) (out []byte, err error) {
	cmdPath, err := exec.LookPath(cmdName)
	if err != nil {
		return
	}
	cmd := exec.Command(cmdPath, args...)
	stdin, err := cmd.StdinPipe()
	if err != nil {
		return
	}
	go func() {
		defer stdin.Close()
		io.WriteString(stdin, stdinText)
	}()

	out, err = cmd.Output()
	return
}

// Unmarshall the first parseable JSON file into out.
func LoadJson(paths []string, out any) error {
	errs := make([]error, 0, len(paths))
	for _, path := range paths {
		data, err := os.ReadFile(path)
		if err != nil {
			errs = append(errs, err)
			continue
		}
		err = json.Unmarshal(data, out)
		if err == nil {
			return nil
		}
		errs = append(errs, err)
	}
	return errors.Join(errs...)
}

// Avoid nil values
func NoopCloser() io.Closer {
	return noopCloser{}
}

type noopCloser struct {
}

func (noopCloser) Close() error {
	return nil
}
