package runner

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path"
	"regexp"

	log "github.com/sirupsen/logrus"

	"github.com/temperlang/temper-docker/common"
	"github.com/temperlang/temper-docker/oci"
)

type Runner struct {
	// command to pass to launcher
	// configure with TT_CMD
	execCommand []string
	// arguments to pass to command
	// these are taken from the command line
	execArgs []string
	// environment to pass
	env map[string]string
	// the container name (based on hostRoot) to run the command in
	containerName string
	// a reference to the docker image to load
	imageName string
	// Identifies the library root on the host, there should be
	// config.temper.md in this directory.
	hostRoot string
	// Relative to hostRoot, this is where the user's working directory is.
	hostWd string
	// The path within the container to mount; should be an empty directory.
	// The working directory in the container will be mountPath/hostWd.
	mountPath string
	// The container UID/GID for mapping the user namespace
	containerUser *common.UidGid
	// The user that is logging in for the present exec session
	execUserName string
	// Determine the strategy for ensuring the container is ready
	ensureContainer oci.EnsureContainerMode
	// logging details
	common.LogConfig
}

func (run Runner) containerWorkingDir() string {
	return path.Join(run.mountPath, run.hostWd)
}

// A container name must be [a-zA-Z0-9][a-zA-Z0-9_.-] and has no limit on length.
// Just make sure the lead is alphanumeric.
var ContainerInvalidCharRe = regexp.MustCompile("[^a-zA-Z0-9_.-]+")

func printHelp() {
	fmt.Print(
		`temper [OPTS] ARGS

The Temper runner executes the Temper command-line interface within a
containerized environment to provide access to a suite of development tools
without manual installation.

The runner will start a container using an existing docker or podman engine,
and open an interactive session if a user terminal is detected.

The container will watch its process and terminate after a period of time if
no process is running.

Help:
  help, --help      the runner will prepend a message to the CLI's normal help command.
  --runner-help     displays this message

Affect command:
  <default>         pass arguments to the Temper CLI
  --                pass arguments after this to the command
  --no-temper       use the first argument as a binary to run instead of Temper
  --shell           bring up a user shell
  --root            bring up a root shell

Container management:
  <default>         use an existing container if possible, pull image if not present
  --no-pull         never pull; exit with an error if image not present
  --new-container   stop and delete an existing container if present

Debugging options:
  --debug           set runner logging level to debug
  --trace           set runner logging level to trace
  --quiet           set runner logging level to fatal
  --log-file        runner logs will be directed to this path
`,
	)
}

func RunnerFromEnv(ae common.ArgsEnv) (*Runner, error) {
	envMap := make(map[string]string, 8)
	var (
		imageName       string = common.BundleRef()
		command         []string
		mountPath       string
		containerUser   *common.UidGid
		execUserName    string
		noCmd           bool
		shell           bool
		loginRoot       bool
		help            int
		logConfig       common.LogConfig
		ensureContainer oci.EnsureContainerMode
	)
	var err error = nil
	ae.ProcessArgs(func(arg []string) int {
		switch arg[0] {
		case "--":
			return -1
		case "--help", "help":
			help = max(help, 1)
			return 0
		case "--runner-help":
			help = max(help, 2)
			return -1
		case "--debug":
			logConfig.Level = log.DebugLevel
			return 1
		case "--trace":
			logConfig.Level = log.TraceLevel
			return 1
		case "--quiet":
			logConfig.Level = log.FatalLevel
			return 1
		case "--log-file":
			if len(arg) < 2 {
				err = errors.New("--log-file expects a file path")
				return -1
			}
			logConfig.Level = max(logConfig.Level, log.DebugLevel)
			logConfig.Path = arg[1]
			logConfig.WhichOutput = common.LogToFile
			return 2
		case "--no-temper":
			noCmd = true
			return 1
		case "--new-container":
			ensureContainer = oci.EnsureContainerNewContainer
			return 1
		case "--no-pull":
			ensureContainer = oci.EnsureContainerNeverPull
			return 1
		case "--img":
			if len(arg) < 2 {
				err = errors.New("--img expects a qualified image reference")
				return -1
			}
			imageName = arg[1]
			return 2
		case "--shell":
			shell = true
			return 1
		case "--root":
			loginRoot = true
			shell = true
			return 1
		case "--path":
			if len(arg) < 2 {
				err = errors.New("--path expects a value for PATH")
				return -1
			}
			envMap["PATH"] = arg[1]
			return 2
		default:
			return 0
		}
	})
	switch help {
	case 1:
		fmt.Println("(For help with the temper runner, use --runner-help.)")
	case 2:
		printHelp()
		err = errors.New("command running aborted by --runner-help")
	}
	if err != nil {
		return nil, err
	}

	simplifiedName, err := common.ParseRefAndSimplify(imageName)
	if err != nil {
		return nil, fmt.Errorf("unable to parse %s: %v", imageName, err)
	}
	for _, image := range common.ImageConfigs {
		if simplifiedName == image.RefWithoutDomain() {
			log.Tracef("Found image=%v", image.FullRef())
			if shell {
				command = image.ShellCmd
			} else if !noCmd {
				command = image.MainCmd
			}
			mountPath = image.MountPath
			for k, v := range image.ImageEnv {
				envMap[k] = v
			}
			containerUser = image.ContainerUser
			if loginRoot {
				execUserName = image.ContainerAdminName
			} else {
				execUserName = image.ContainerUserName
			}
			break
		}
	}

	transitEnv(logConfig, envMap)

	workDir, err := os.Getwd()
	if err != nil {
		return nil, fmt.Errorf("couldn't get working directory failed: %w", err)
	}
	libConfig, err := common.FindTemperLibraryRootFrom(workDir)
	if err != nil {
		if errors.Is(err, common.ErrNoConfigFound) {
			log.Warn(err)
		} else {
			return nil, fmt.Errorf("finding library root: %w", err)
		}
	}

	log.Tracef("command=%v args=%v", command, ae.Args)

	simplePath := ContainerInvalidCharRe.ReplaceAllString("/"+libConfig.LibraryRoot, "_")
	return &Runner{
		imageName:       imageName,
		hostRoot:        libConfig.LibraryRoot,
		hostWd:          libConfig.WorkingDirRelative,
		containerName:   common.CONTAINER_LEAD + simplePath,
		execCommand:     command,
		execArgs:        ae.Args,
		env:             envMap,
		mountPath:       mountPath,
		containerUser:   containerUser,
		execUserName:    execUserName,
		LogConfig:       logConfig,
		ensureContainer: ensureContainer,
	}, nil
}

func (run *Runner) Run(ctx context.Context) (exitCode int, err error) {
	exitCode = common.FAIL_CODE
	log.Debug("Ensuring container")
	cnt, err := oci.EnsureContainer(ctx, oci.ContainerDetails{
		ImageName:     run.imageName,
		ContainerName: run.containerName,
		MountContTgt:  run.mountPath,
		MountHostSrc:  run.hostRoot,
		ContainerUser: run.containerUser,
		EnsureMode:    run.ensureContainer,
	})
	if err != nil {
		return exitCode, err
	}
	cntId := cnt.ContainerID()
	log.Debugf("Starting container %v", cntId)
	err = oci.ContainerStart(ctx, cntId)
	if err != nil {
		return exitCode, err
	}

	res, err := oci.ContainerExec(ctx,
		oci.ContainerExecDetails{
			ContainerId: cntId,
			Command:     common.Concat(run.execCommand, run.execArgs),
			Env:         run.env,
			WorkingDir:  run.containerWorkingDir(),
			User:        run.execUserName,
		},
	)

	if err != nil {
		return exitCode, fmt.Errorf("inspecting execution after command: %w", err)
	}
	pid := res.Pid()
	exitCode = res.ExitCode()
	log.Debugf("Process %d exited with code %d", pid, exitCode)
	return exitCode, nil
}
