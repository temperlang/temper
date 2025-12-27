package xdg

// Implements https://wiki.archlinux.org/title/XDG_Base_Directory
// Does some simple caching using sync.Once

import (
	"fmt"
	"os"
	"path/filepath"
	"sync"
)

func Home(plus string) (string, error) {
	return also(homeDir.Run(), plus)
}

// XDG standard only on Unix
func Config(plus string) (string, error) {
	return also(configDir.Run(), plus)
}

// XDG standard only on Unix
func CacheHome(plus string) (string, error) {
	return also(cacheHome.Run(), plus)
}

// XDG standard only on Unix
func DataHome(plus string) (string, error) {
	return also(dataHome.Run(), plus)
}

func StateHome(plus string) (string, error) {
	return also(stateHome.Run(), plus)
}

func RuntimeHome(plus string) (string, error) {
	return also(runtimeHome.Run(), plus)
}

// Decorate a result with an additional relative path
func also(result pathErr, plus string) (string, error) {
	if result.err != nil {
		return "", result.err
	}
	if plus == "" {
		return result.path, nil
	}
	return filepath.Join(result.path, filepath.FromSlash(plus)), nil
}

type pathErr struct {
	path string
	err  error
}

type thunk struct {
	once   sync.Once
	result pathErr
	fn     func(*pathErr)
}

func (thnk *thunk) Run() pathErr {
	thnk.once.Do(func() {
		thnk.fn(&thnk.result)
	})
	return thnk.result
}

type homeAnd struct {
	envVar   string
	homeJoin string
	once     sync.Once
	result   pathErr
}

func (thnk *homeAnd) Run() pathErr {
	thnk.once.Do(func() {
		if dat, ok := os.LookupEnv(thnk.envVar); ok {
			thnk.result.path = dat
			thnk.result.err = nil
		} else {
			path, err := Home(thnk.homeJoin)
			thnk.result = pathErr{path, err}
		}
	})
	return thnk.result
}

// We could also use os/user, but this utility is meant to be called from a
// properly configured terminal, so relying on $HOME is not unreasonable.
var homeDir = thunk{
	fn: func(result *pathErr) {
		dir, err := os.UserHomeDir()
		result.path = dir
		result.err = err
	},
}

// On unix, this is normally XDG_CONFIG or $HOME/.config
var configDir = thunk{
	fn: func(result *pathErr) {
		dir, err := os.UserConfigDir()
		result.path = dir
		result.err = err
	},
}

// On unix, this is normally XFG_CACHE_HOME or $HOME/.cache
var cacheHome = thunk{
	fn: func(result *pathErr) {
		dir, err := os.UserCacheDir()
		result.path = dir
		result.err = err
	},
}

var dataHome = homeAnd{
	envVar:   "XDG_DATA_HOME",
	homeJoin: ".local/share",
}

var stateHome = homeAnd{
	envVar:   "XDG_STATE_HOME",
	homeJoin: ".local/state",
}

var runtimeHome = thunk{
	fn: func(result *pathErr) {
		if dat, ok := os.LookupEnv("XDG_RUNTIME_DIR"); ok {
			result.path = dat
			result.err = nil
		} else {
			result.path = filepath.FromSlash(fmt.Sprintf("/run/user/%d", os.Getuid()))
		}
	},
}
