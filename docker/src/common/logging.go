package common

import (
	"bufio"
	"bytes"
	"context"
	"fmt"
	"io"
	"net"
	"os"
	"runtime"

	log "github.com/sirupsen/logrus"
)

const runnerPrefix = "run:"
const launcherEntrypointPrefix = "loop:"
const launcherExecPrefix = "exec:"

type LogConfig struct {
	Level           log.Level
	Path            string
	WhichOutput     LogToOutput
	ContainerSocket string
}

type LogToOutput int

const (
	LogToStderr LogToOutput = iota
	LogToNull
	LogToFile
)

// For the launcher process, default to hiding logs
func SetupDebugLauncher(ctx context.Context, cfg LogConfig) (io.Closer, error) {
	// If we're logging to stderr, we can pass directly back to the runner via stderr.
	if cfg.WhichOutput == LogToStderr {
		return stderrLogger(launcherExecPrefix, cfg.Level)
	}
	// Otherwise, the launcher's logging can take over.
	conn, err := net.Dial("unix", cfg.ContainerSocket)
	// Can't reach the socket, then discard.
	if err != nil {
		fmt.Fprintf(
			os.Stderr, "Socket unavailable %s: %v\nFall back to stderr", cfg.ContainerSocket, err,
		)
		discardingLogger()
		return NoopCloser(), nil
	}
	setTextLogs(launcherExecPrefix, true, cfg.Level, conn)
	return conn, nil
}

// For the runner process, default to hiding logs
func SetupDebugRunner(cfg LogConfig) (cl io.Closer, err error) {
	switch cfg.WhichOutput {
	case LogToStderr:
		cl, err = stderrLogger(runnerPrefix, cfg.Level)
	case LogToFile:
		cl, err = fileLoggerByPath(runnerPrefix, cfg.Path, cfg.Level)
	default:
		// Also LogToNull
		discardingLogger()
	}
	if err != nil {
		discardingLogger()
	}
	if cl == nil {
		cl = NoopCloser()
	}
	return
}

func prefixPrettify(prefix string) func(f *runtime.Frame) (string, string) {
	return func(f *runtime.Frame) (string, string) {
		return prefix + f.Function, f.File
	}
}

func setTextLogs(prefix string, disableColors bool, level log.Level, writer io.Writer) {
	log.SetLevel(level)
	log.SetFormatter(&log.TextFormatter{
		PadLevelText:     true,
		DisableColors:    disableColors,
		CallerPrettyfier: prefixPrettify(prefix),
	})
	log.SetOutput(writer)
}

// This tries to open a logger. If err != nil, then no logger has been pushed.
func fileLoggerByPath(prefix string, path string, level log.Level) (io.Closer, error) {
	file, err := os.OpenFile(path, os.O_APPEND|os.O_WRONLY|os.O_CREATE, 0644)
	if err != nil {
		return stderrLogger(prefix, log.FatalLevel)
	}
	log.SetLevel(level)
	log.SetFormatter(&log.JSONFormatter{
		CallerPrettyfier: prefixPrettify(prefix),
	})
	log.SetOutput(file)
	return file, nil
}

// Discard logging
func discardingLogger() {
	log.SetLevel(log.PanicLevel)
	log.SetOutput(io.Discard)
}

// Log to stderr.
func stderrLogger(prefix string, level log.Level) (io.Closer, error) {
	setTextLogs(prefix, false, level, os.Stderr)
	return NoopCloser(), nil
}

// For the entrypoint process, we write to stderr as it's visible to the
// container stream
func SetupDebugEntryPoint(ctx context.Context, cfg LogConfig) (io.Closer, error) {
	listen, err := net.Listen("unix", cfg.ContainerSocket)
	// Can't reach the socket, just do simple stderr logging
	if err != nil {
		fmt.Fprintf(
			os.Stderr, "Socket unavailable %s: %v\nFall back to stderr", cfg.ContainerSocket, err,
		)
		return stderrLogger(launcherEntrypointPrefix, cfg.Level)
	}
	clCtx, cancelFunc := context.WithCancel(ctx)
	closer := clCloser{cancelFunc, listen}

	loggerChan := make(chan []byte, 1)
	pipeChan := make(chan []byte, 1)
	logReader, logWriter := io.Pipe()
	go sitOnChannels(clCtx, loggerChan, pipeChan)
	go sitOnReader(clCtx, logReader, loggerChan)
	go sitOnSocket(clCtx, listen, pipeChan)

	setTextLogs(launcherEntrypointPrefix, true, cfg.Level, logWriter)
	return closer, nil
}

type clCloser struct {
	channelCancel context.CancelFunc
	socket        io.Closer
}

func (cc clCloser) Close() error {
	cc.channelCancel()
	return cc.socket.Close()
}

func sitOnChannels(ctx context.Context, a chan []byte, b chan []byte) {
	fh := os.Stderr
	log.Trace("sitOnChannels: starting")
	done := ctx.Done()
	for {
		var line []byte
		select {
		case <-done:
			log.Debug("sitOnChannels: cancellation signal received")
			return
		case line = <-a:
		case line = <-b:
		}
		fh.Write(line)
		fh.Write(newlineSeqBytes)
	}
}

var newlineSeqBytes []byte = bytes.NewBufferString(fmt.Sprintln("")).Bytes()

func sitOnReader(ctx context.Context, rdr io.Reader, plex chan []byte) {
	log.Trace("sitOnReader: starting")
	scanner := bufio.NewScanner(rdr)
	for scanner.Scan() && ctx.Err() == nil {
		line := scanner.Bytes()
		log.Trace("sitOnReader: sending line to channel")
		plex <- line
		log.Trace("sitOnReader: sent")
	}
	log.Debugf("sitOnReader: cancellation %v", ctx.Err())
}

func sitOnSocket(ctx context.Context, sckt net.Listener, plex chan []byte) {
	for {
		conn, err := sckt.Accept()
		if err != nil {
			log.Warnf("sitOnSocket: stopping listening: %v", err)
			return
		}
		go func(c net.Conn) {
			log.Trace("sitOnSocket: new connection")
			scanner := bufio.NewScanner(c)
			for scanner.Scan() {
				line := scanner.Bytes()
				log.Trace("sitOnReader: sending line to channel")
				plex <- line
				log.Trace("sitOnReader: sent")
			}
			log.Trace("sitOnSocket: connection done")
		}(conn)
	}
}
