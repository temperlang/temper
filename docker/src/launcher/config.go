package launcher

import (
	"encoding/json"
	"time"

	log "github.com/sirupsen/logrus"
	"github.com/temperlang/temper-docker/common"
	"github.com/temperlang/temper-docker/xdg"
)

type LauncherConfig struct {
	PidPath        string
	KeepAlive      time.Duration
	CheckFrequency time.Duration
	Command        []string
	// logging details
	common.LogConfig
}

func NewLauncherConfig(ae common.ArgsEnv) (cfg LauncherConfig, err error) {
	pidPath, err := xdg.Home(common.DEFAULT_PID_FILE)
	if err != nil {
		return
	}
	containerSocket, err := xdg.Home(common.DEFAULT_LOG_SOCKET)
	if err != nil {
		return
	}
	keepAlive, err := time.ParseDuration(ae.TakeEnv("TT_DURATION", ""))
	if err != nil {
		keepAlive = 4 * time.Minute
	}
	checkFrequency, err := time.ParseDuration(ae.TakeEnv("TT_FREQ", ""))
	if err != nil {
		checkFrequency = 10 * time.Second
	}
	logConfig := common.LogConfig{}
	err = json.Unmarshal([]byte(ae.TakeEnv(common.TT_LOG, `"fail"`)), &logConfig)
	if err != nil {
		logConfig.Level = log.WarnLevel
		logConfig.WhichOutput = common.LogToStderr
	}
	logConfig.ContainerSocket = containerSocket

	return LauncherConfig{
		PidPath:        pidPath,
		KeepAlive:      keepAlive,
		CheckFrequency: checkFrequency,
		Command:        ae.Args,
		LogConfig:      logConfig,
	}, nil
}
