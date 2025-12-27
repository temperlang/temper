//go:build windows

package oci

import (
	"context"
	"time"

	"github.com/moby/term"
)

func getTermFdInfo(stdin interface{}, stdout interface{}) (uintptr, bool) {
	return term.GetFdInfo(stdout)
}

func notifyWinChange(ctx context.Context, winChange chan term.Winsize, termFd uintptr) {
	// Simulate WINCH with polling
	go func() {
		var lastW uint16
		var lastH uint16

		d := time.Millisecond * 250
		timer := time.NewTimer(d)
		defer timer.Stop()
		for ; ; timer.Reset(d) {
			select {
			case <-ctx.Done():
				return
			case <-timer.C:
				break
			}

			ws, err := term.GetWinsize(termFd)
			if err != nil || ws == nil {
				continue
			}
			h := ws.Height
			w := ws.Width
			if w != lastW || h != lastH {
				winChange <- *ws
				lastW, lastH = w, h
			}
		}
	}()
}
