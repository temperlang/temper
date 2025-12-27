package main

import (
	"context"
	"fmt"
	"io"
	"os"

	log "github.com/sirupsen/logrus"
	"github.com/temperlang/temper-docker/common"
	"github.com/temperlang/temper-docker/launcher"
)

// Run without arguments, this will monitor active processes.
// Run with arguments, this will launch a process to be monitored.
func mainInner() error {
	var (
		err    error
		cl     io.Closer
		config launcher.LauncherConfig
	)

	ctx := context.Background()
	config, err = launcher.NewLauncherConfig(common.MakeArgsEnvOs())
	if err != nil {
		return err
	}
	usingLauncher := len(config.Command) > 0

	var details string
	if usingLauncher {
		details = "launcher"
		cl, err = common.SetupDebugLauncher(ctx, config.LogConfig)
	} else {
		details = "container entrypoint"
		cl, err = common.SetupDebugEntryPoint(ctx, config.LogConfig)
	}

	if err != nil {
		return err
	}
	defer cl.Close()

	if usingLauncher {
		log.Debugf("Launch process %v", config.Command)
		err = launcher.LaunchProc(config, config.Command)
	} else {
		log.Info("Watch loop")
		err = launcher.CheckActiveLoop(config)
	}
	return fmt.Errorf("within %s: %v", details, err)
}

// Display the error output, if any.
func main() {
	err := mainInner()
	if err == nil {
		os.Exit(0)
		return
	}
	fmt.Fprintln(os.Stderr, err.Error())
	os.Exit(1)
}
