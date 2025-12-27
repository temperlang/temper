package main

import (
	"context"
	"fmt"
	"io"
	"os"

	"github.com/temperlang/temper-docker/common"
	"github.com/temperlang/temper-docker/oci"
	"github.com/temperlang/temper-docker/runner"
)

func mainInner() (code int, err error) {
	var cl io.Closer
	code = common.FAIL_CODE
	run, err := runner.RunnerFromEnv(common.MakeArgsEnvOs())
	if err != nil {
		return
	}
	cl, err = common.SetupDebugRunner(run.LogConfig)
	if err != nil {
		return
	}
	defer cl.Close()
	ctx, err := oci.NewClient(context.Background())
	if err != nil {
		return
	}
	code, err = run.Run(ctx)
	return
}

func main() {
	code, err := mainInner()
	if err != nil {
		fmt.Fprintln(os.Stderr, err.Error())
	}
	os.Exit(int(code))
}
