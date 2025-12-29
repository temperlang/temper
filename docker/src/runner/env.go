package runner

import (
	"os"
	"strings"

	"encoding/json"

	"github.com/temperlang/temper-docker/common"
)

var envVars = [6]string{
	// terminal variables
	"TERM",
	"TERMINFO",
	"TERMCAP",
	"TZ",
}

// Given an environment map, update it with everything that
// can be inferred from the os environment.
func transitEnv(cfg common.LogConfig, env map[string]string) {
	for _, key := range envVars {
		value, found := os.LookupEnv(key)
		if found {
			env[key] = value
		}
	}
	// Only pass debug to stderr along.
	if cfg.WhichOutput == common.LogToFile {
		cfg.WhichOutput = common.LogToNull
		cfg.Path = ""
	}
	logBytes, err := json.Marshal(cfg)
	if err == nil {
		env[common.TT_LOG] = string(logBytes)
	} else {
		env[common.TT_LOG] = "default"
	}

	// Set the locale to C and preserve the encoding.
	// Ref: https://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap08.html
	// A locale includes [language[_territory][.codeset][@modifier]]
	// We read LC_CTYPE to check encoding.
	lcAll := "C.UTF-8"
	for _, key := range []string{"LC_CTYPE", "LC_ALL", "LANG"} {
		// Extract codeset[@modifier]
		parts := strings.SplitN(os.Getenv(key), ".", 3)
		if len(parts) < 2 {
			continue
		}
		// Extract codeset
		lcAll = "C." + strings.SplitN(parts[1], "@", 2)[0]
		break
	}
	env["LC_ALL"] = lcAll
}
