package gpgme

import (
	"io"
	"os"
)

const (
	SeekSet = iota
	SeekCur
	SeekEnd
)

// The Data buffer used to communicate with GPGME
type Data struct {
}

// NewData returns a new memory based data buffer
func NewData() (*Data, error) {
	return nil, errStub
}

// NewDataFile returns a new file based data buffer
func NewDataFile(f *os.File) (*Data, error) {
	return nil, errStub
}

// NewDataBytes returns a new memory based data buffer that contains `b` bytes
func NewDataBytes(b []byte) (*Data, error) {
	return nil, errStub
}

// NewDataReader returns a new callback based data buffer
func NewDataReader(r io.Reader) (*Data, error) {
	return nil, errStub
}

// NewDataWriter returns a new callback based data buffer
func NewDataWriter(w io.Writer) (*Data, error) {
	return nil, errStub
}

// NewDataReadWriter returns a new callback based data buffer
func NewDataReadWriter(rw io.ReadWriter) (*Data, error) {
	return nil, errStub
}

// NewDataReadWriteSeeker returns a new callback based data buffer
func NewDataReadWriteSeeker(rw io.ReadWriteSeeker) (*Data, error) {
	return nil, errStub
}

// Close releases any resources associated with the data buffer
func (d *Data) Close() error {
	return nil
}

func (d *Data) Write(p []byte) (int, error) {
	return 0, errStub
}

func (d *Data) Read(p []byte) (int, error) {
	return 0, errStub
}

func (d *Data) Seek(offset int64, whence int) (int64, error) {
	return 0, errStub
}

// Name returns the associated filename if any
func (d *Data) Name() string {
	return ""
}
