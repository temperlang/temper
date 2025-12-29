package oci

import (
	"context"
	"io"

	"github.com/moby/term"
	log "github.com/sirupsen/logrus"
)

type termInfo struct {
	env       map[string]string
	tty       bool
	fileIn    io.ReadCloser
	fileInFd  uintptr
	fileOut   io.Writer
	fileOutFd uintptr
	fileErr   io.Writer
	termFd    uintptr
	size      term.Winsize
}

func newTermInfo(ctx context.Context, env map[string]string) termInfo {
	fileIn, fileOut, fileErr := term.StdStreams()
	fileInFd, _ := term.GetFdInfo(fileIn)
	fileOutFd, _ := term.GetFdInfo(fileOut)
	termFd, tty := getTermFdInfo(fileIn, fileOut)
	// If you don't set a console size, some terminal detection assumes
	// it's not an interactive session.
	size := term.Winsize{
		Height: 80, Width: 24,
	}
	if tty {
		if _, present := env["TERM"]; !present {
			env["TERM"] = "xterm-color"
		}
		ws, err := term.GetWinsize(termFd)
		if err != nil || ws == nil {
			log.Warnf("failed to obtain TTY size: %v", err)
		} else {
			size = *ws
		}
	}
	return termInfo{
		env:       env,
		tty:       tty,
		fileIn:    fileIn,
		fileInFd:  fileInFd,
		fileOut:   fileOut,
		fileOutFd: fileOutFd,
		fileErr:   fileErr,
		termFd:    termFd,
		size:      size,
	}
}
