package launcher

import (
	"bufio"
	"errors"
	"fmt"
	"os"
	"strconv"
	"strings"
	"syscall"
	"time"

	log "github.com/sirupsen/logrus"
)

// An error to represent normal shutdown from the checkActive loop.
var errShutdownNow = errors.New("normal shutdown")

// Check whether the container should continue. The desired behavior is that the container
// stays open while a managed process is running, and then waits for about two minutes
// when the last process exits.

// 1. Looks for pids in the pidfile. If any are active, the container will stay open.
// 2. When a pid is *not* detected, the pidfile is updated. This also updates the file's mtime.

// The container should continue when:

// 1. any active processes are present
// 2. any change to the pidfile was made
// 3. the file has been modified in the last two minutes

func checkActive(config LauncherConfig) error {
	file, err := pidfileOpener(config, os.O_RDWR)
	if err != nil {
		return err
	}
	defer file.Close()
	info, err := file.Stat()
	if err != nil {
		return err
	}
	mt := info.ModTime()
	mt = mt.Add(config.KeepAlive)
	var pids []int
	var change bool
	scan := bufio.NewScanner(file)
	for scan.Scan() {
		line := strings.Trim(scan.Text(), WHITESPACE)
		if line == "" {
			continue
		}
		pid, err := strconv.Atoi(line)
		if err != nil {
			log.Warnf("checkActive: bad entry '%v'", line)
			change = true
			continue
		}
		// There is no 0 signal, but kill will verify a process exists that
		// we _could_ send a signal to.
		err = syscall.Kill(pid, 0)
		if err == nil {
			pids = append(pids, pid)
		} else {
			log.Debugf("checkActive: process %d dead", pid)
			change = true
		}
	}
	if change {
		log.Debugf("checkActive: change detected, rewrite pidfile")
		_, err = file.Seek(0, 0)
		if err != nil {
			return err
		}
		err = file.Truncate(0)
		if err != nil {
			return err
		}
		for _, pid := range pids {
			log.Debugf("checkActive: add %d", pid)
			_, err = fmt.Fprintln(file, pid)
			if err != nil {
				return err
			}
		}
		return nil
	}
	if npids := len(pids); npids > 0 {
		log.Debugf("checkActive: %d processes alive", npids)
		return nil
	}
	if now := time.Now(); now.Before(mt) {
		log.Debugf("checkActive: no processes, waiting %v until terminate", mt.Sub(now))
		return nil
	}
	log.Debug("checkActive: normal shutdown")
	return errShutdownNow
}

// Loop until container stop conditions are complete.
func CheckActiveLoop(config LauncherConfig) error {
	freq := config.CheckFrequency
	log.Debugf("Launcher: initial sleep %s", freq)
	time.Sleep(freq)
	var err error = nil
	for {
		log.Debug("Launcher: check for active processes")
		err = checkActive(config)
		if err != nil {
			break
		}
		log.Debugf("Launcher: active processes, sleep %s", freq)
		time.Sleep(freq)
	}
	if err == errShutdownNow {
		return nil
	}
	return err
}
