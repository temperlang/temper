package oci

import (
	"context"
	"fmt"
	"io"

	"github.com/docker/docker/api/types"
	docker "github.com/docker/docker/client"
	"github.com/docker/docker/pkg/stdcopy"
	"github.com/moby/term"
	log "github.com/sirupsen/logrus"
	"github.com/temperlang/temper-docker/common"
)

type channel int

const (
	input channel = iota
	output
)

type report struct {
	which channel
	err   error
}

func dkrContainerExec(
	ctx context.Context,
	client *docker.Client,
	details ContainerExecDetails,
) (ContainerExecInfo, error) {
	ti := newTermInfo(ctx, details.Env)

	exc, err := client.ContainerExecCreate(
		ctx,
		details.ContainerId,
		types.ExecConfig{
			Tty:          ti.tty,
			AttachStdin:  true,
			AttachStderr: true,
			AttachStdout: true,
			Cmd:          details.Command,
			Env:          common.EnvPairsFromMap(details.Env),
			WorkingDir:   details.WorkingDir,
			ConsoleSize:  twoUint(&ti.size),
			User:         details.User,
		},
	)
	if err != nil {
		return nil, fmt.Errorf("creating container execution: %w", err)
	}
	sessionID := exc.ID

	var hijacked types.HijackedResponse
	hijacked, err = client.ContainerExecAttach(ctx, sessionID, types.ExecStartCheck{
		Detach:      false,
		Tty:         ti.tty,
		ConsoleSize: twoUint(&ti.size),
	})
	if err != nil {
		return nil, fmt.Errorf("attaching to container execution: %w", err)
	}
	defer hijacked.Close()

	mediaType, foundMediaType := hijacked.MediaType()
	multiplexed := foundMediaType && mediaType == types.MediaTypeMultiplexedStream

	if ti.tty {
		stateIn, err := term.SetRawTerminal(ti.fileInFd)
		if err != nil {
			return nil, fmt.Errorf("set raw terminal: %w", err)
		}
		if stateIn != nil {
			defer term.RestoreTerminal(ti.fileInFd, stateIn)
		}
		stateOut, err := term.SetRawTerminalOutput(ti.fileOutFd)
		if err != nil {
			return nil, fmt.Errorf("set raw terminal: %w", err)
		}
		if stateOut != nil {
			defer term.RestoreTerminal(ti.fileOutFd, stateOut)
		}
		// Write all output to the output stream when interactive.
		ti.fileErr = ti.fileOut

		winChange := make(chan term.Winsize, 2)
		winCtx, winCancel := context.WithCancel(ctx)
		defer winCancel()

		notifyWinChange(winCtx, winChange, ti.termFd)
		go attachHandleResize(ctx, client, winCtx, winChange, sessionID)
	}

	// we wait on output to complete
	done := make(chan report)

	go func() {
		var err error
		if multiplexed {
			// StdCopy demultiplexes the stream to the standard channels
			_, err = stdcopy.StdCopy(ti.fileOut, ti.fileErr, hijacked.Conn)
		} else {
			_, err = io.Copy(ti.fileOut, hijacked.Conn)
		}
		done <- report{output, err}
	}()

	go func() {
		_, err := io.Copy(hijacked.Conn, ti.fileIn)
		done <- report{input, err}
	}()

	outputWaiting := true
	for outputWaiting {
		select {
		case rpt := <-done:
			if rpt.err != nil {
				return nil, fmt.Errorf("during attached stream: %w", rpt.err)
			}
			if rpt.which == output {
				outputWaiting = false
			}
		case <-ctx.Done():
			return nil, fmt.Errorf("from context during attached stream: %w", ctx.Err())
		}
	}
	return dkrContainerExecInspect(ctx, client, sessionID)
}

// This is intended to not be run as a goroutine, handling resizing for a container
// or exec session. It will call resize once and then starts a goroutine which calls resize on winChange
func attachHandleResize(ctx context.Context, client *docker.Client, winCtx context.Context, winChange chan term.Winsize, execID string) {
	for {
		select {
		case <-winCtx.Done():
			return
		case ws := <-winChange:
			err := dkrContainerExecResize(ctx, client, execID, ws)
			if err != nil {
				log.Warnf("failed to resize TTY: %v", err)
			}
		}
	}
}
