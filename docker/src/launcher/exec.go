package launcher

import (
	"fmt"
	"os"
	"os/exec"
	"syscall"

	log "github.com/sirupsen/logrus"
)

// Set a new process ID to monitor. This interacts with check_active by:
//
// 1. Gaining a lock on the pidfile.
// 2. Appending the PID to the pidfile.
// 3. Implicitly updating the mtime of the pidfile.
func addPid(config LauncherConfig, pid int) error {
	log.Tracef("Add pid %d", pid)
	file, err := pidfileOpener(config, os.O_APPEND|os.O_WRONLY)
	if err != nil {
		return err
	}
	defer file.Close()
	log.Trace("Writing to pidfile")
	_, err = fmt.Fprintln(file, pid)
	return err
}

// Launch a process via exec, and make sure the process ID is monitored.
func LaunchProc(config LauncherConfig, argv []string) error {
	log.Trace("Adding current PID to pidfile")
	err := addPid(config, os.Getpid())
	if err != nil {
		return fmt.Errorf("while adding pid: %v", err)
	}
	log.Tracef("Look up command %s", argv[0])
	path, err := exec.LookPath(argv[0])
	if err != nil {
		return fmt.Errorf("while looking up '%s' on PATH: %v", argv[0], err)
	}
	env := os.Environ()
	log.Tracef("Exec %s with %v\n%v", path, argv, env)
	err = syscall.Exec(path, argv, env)
	return fmt.Errorf("while executing '%s': %v", path, err)
}
