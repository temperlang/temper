//go:build !windows

package oci

import (
	"context"
	"os"
	"os/signal"
	"syscall"

	"github.com/moby/term"
)

func getTermFdInfo(stdin interface{}, stdout interface{}) (uintptr, bool) {
	return term.GetFdInfo(stdout)
}

func notifyWinChange(ctx context.Context, winChange chan term.Winsize, termFd uintptr) {
	sigs := make(chan os.Signal, 1)
	signal.Notify(sigs, syscall.SIGWINCH)
	go func() {
		ws, err := term.GetWinsize(termFd)
		if err == nil && ws != nil {
			winChange <- *ws
		}
		for {
			select {
			case <-ctx.Done():
				return
			case <-sigs:
				ws, err := term.GetWinsize(termFd)
				if err == nil && ws != nil {
					winChange <- *ws
				}
			}
		}
	}()
}
