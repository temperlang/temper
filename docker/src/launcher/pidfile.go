package launcher

import (
	"os"
	"syscall"

	log "github.com/sirupsen/logrus"
)

// Make sure to create the file atomically if needed, and block until a lock is acquired.
// This is used as an opener argument to `open`.
func pidfileOpener(config LauncherConfig, flags int) (*os.File, error) {

	log.Tracef("Open pidfile %s", config.PidPath)
	file, err := os.OpenFile(config.PidPath, flags|os.O_CREATE, 0600)
	if err != nil {
		return nil, err
	}
	err = syscall.Flock(int(file.Fd()), syscall.LOCK_EX)
	if err != nil {
		file.Close()
		return nil, err
	}
	return file, nil
}

// Whitespace allowed in the pid file.
const WHITESPACE = " \t\r\n"
