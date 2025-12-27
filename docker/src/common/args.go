package common

import (
	"os"
	"strings"
)

type ArgsEnv struct {
	Args []string
	Env  map[string]string
}

func MakeArgsEnvOs() ArgsEnv {
	return MakeArgsEnv(os.Args[1:], os.Environ())
}

func MakeArgsEnv(args []string, env []string) ArgsEnv {
	out := ArgsEnv{}
	out.Env = make(map[string]string, len(env))
	for _, p := range env {
		if key, val, found := strings.Cut(p, "="); found {
			out.Env[key] = val
		}
	}
	out.Args = append(make([]string, 0, len(args)), os.Args[1:]...)
	return out
}

func (ae ArgsEnv) TakeEnv(name string, def string) string {
	if result, ok := ae.Env[name]; ok {
		delete(ae.Env, name)
		return result
	}
	return def
}

func (ae *ArgsEnv) ProcessArgs(handler func([]string) int) {
	args := ae.Args
	lenArgs := len(args)
	idx := 0
	for idx < lenArgs {
		used := handler(args[idx:])
		if used < 0 {
			idx -= used
			break
		}
		if used == 0 {
			break
		}
		idx += used
	}
	ae.Args = args[idx:]
}
