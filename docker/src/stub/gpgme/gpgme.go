// The real gpgme package provides a Go wrapper for the GPGME library
// This package stubs it out as we're not using PGPME signing
package gpgme

import (
	"errors"
	"io"
	"os"
	"time"
)

var Version string = "stub"
var errStub = errors.New("gpgme module is stubbed out")

type Callback func(uidHint string, prevWasBad bool, f *os.File) error

type Protocol int

const (
	ProtocolOpenPGP Protocol = iota
	ProtocolCMS
	ProtocolGPGConf
	ProtocolAssuan
	ProtocolG13
	ProtocolUIServer
	ProtocolDefault
	ProtocolUnknown
)

type PinEntryMode int

type EncryptFlag uint

const (
	EncryptAlwaysTrust EncryptFlag = iota
	EncryptNoEncryptTo
	EncryptPrepare
	EncryptExceptSign
)

type HashAlgo int

type KeyListMode uint

const (
	KeyListModeLocal KeyListMode = iota
	KeyListModeExtern
	KeyListModeSigs
	KeyListModeSigNotations
	KeyListModeEphemeral
	KeyListModeModeValidate
)

type PubkeyAlgo int

type SigMode int

const (
	SigModeNormal SigMode = iota
	SigModeDetach
	SigModeClear
)

type SigSum int

const (
	SigSumValid SigSum = iota
	SigSumGreen
	SigSumRed
	SigSumKeyRevoked
	SigSumKeyExpired
	SigSumSigExpired
	SigSumKeyMissing
	SigSumCRLMissing
	SigSumCRLTooOld
	SigSumBadPolicy
	SigSumSysError
)

type Validity int

const (
	ValidityUnknown Validity = iota
	ValidityUndefined
	ValidityNever
	ValidityMarginal
	ValidityFull
	ValidityUltimate
)

type ErrorCode int

const (
	ErrorNoError ErrorCode = iota
	ErrorEOF
)

// Error is a wrapper for GPGME errors
type Error struct {
}

func (e Error) Code() ErrorCode {
	return 0
}

func (e Error) Error() string {
	return "stub"
}

func EngineCheckVersion(p Protocol) error {
	return errStub
}

type EngineInfo struct {
}

func (e *EngineInfo) Next() *EngineInfo {
	return nil
}

func (e *EngineInfo) Protocol() Protocol {
	return 0
}

func (e *EngineInfo) FileName() string {
	return "stub"
}

func (e *EngineInfo) Version() string {
	return "stub"
}

func (e *EngineInfo) RequiredVersion() string {
	return "stub"
}

func (e *EngineInfo) HomeDir() string {
	return ""
}

func GetEngineInfo() (*EngineInfo, error) {
	return nil, errStub
}

func SetEngineInfo(proto Protocol, fileName, homeDir string) error {
	return nil
}

func FindKeys(pattern string, secretOnly bool) ([]*Key, error) {
	return nil, errStub
}

func Decrypt(r io.Reader) (*Data, error) {
	return nil, errStub
}

type Context struct {
	Key      *Key
	KeyError error
}

func New() (*Context, error) {
	return nil, errStub
}

func (c *Context) Release() {
}

func (c *Context) SetArmor(yes bool) {
}

func (c *Context) Armor() bool {
	return false
}

func (c *Context) SetTextMode(yes bool) {
}

func (c *Context) TextMode() bool {
	return false
}

func (c *Context) SetProtocol(p Protocol) error {
	return errStub
}

func (c *Context) Protocol() Protocol {
	return 0
}

func (c *Context) SetKeyListMode(m KeyListMode) error {
	return errStub
}

func (c *Context) KeyListMode() KeyListMode {
	return 0
}

func (c *Context) SetCallback(callback Callback) error {
	return errStub
}

func (c *Context) EngineInfo() *EngineInfo {
	return nil
}

func (c *Context) SetEngineInfo(proto Protocol, fileName, homeDir string) error {
	return errStub
}

func (c *Context) KeyListStart(pattern string, secretOnly bool) error {
	return errStub
}

func (c *Context) KeyListNext() bool {
	return false
}

func (c *Context) KeyListEnd() error {
	return errStub
}

func (c *Context) GetKey(fingerprint string, secret bool) (*Key, error) {
	return nil, errStub
}

func (c *Context) Decrypt(ciphertext, plaintext *Data) error {
	return errStub
}

func (c *Context) DecryptVerify(ciphertext, plaintext *Data) error {
	return errStub
}

type Signature struct {
	Summary        SigSum
	Fingerprint    string
	Status         error
	Timestamp      time.Time
	ExpTimestamp   time.Time
	WrongKeyUsage  bool
	PKATrust       uint
	ChainModel     bool
	Validity       Validity
	ValidityReason error
	PubkeyAlgo     PubkeyAlgo
	HashAlgo       HashAlgo
}

func (c *Context) Verify(sig, signedText, plain *Data) (string, []Signature, error) {
	return "", nil, errStub
}

func (c *Context) Encrypt(recipients []*Key, flags EncryptFlag, plaintext, ciphertext *Data) error {
	return errStub
}

func (c *Context) Sign(signers []*Key, plain, sig *Data, mode SigMode) error {
	return errStub
}

type AssuanDataCallback func(data []byte) error
type AssuanInquireCallback func(name, args string) error
type AssuanStatusCallback func(status, args string) error

// AssuanSend sends a raw Assuan command to gpg-agent
func (c *Context) AssuanSend(
	cmd string,
	data AssuanDataCallback,
	inquiry AssuanInquireCallback,
	status AssuanStatusCallback,
) error {
	return errStub
}

// ExportModeFlags defines how keys are exported from Export
type ExportModeFlags uint

const (
	ExportModeExtern ExportModeFlags = iota
	ExportModeMinimal
)

func (c *Context) Export(pattern string, mode ExportModeFlags, data *Data) error {
	return errStub
}

// ImportStatusFlags describes the type of ImportStatus.Status. The C API in gpgme.h simply uses "unsigned".
type ImportStatusFlags uint

const (
	ImportNew ImportStatusFlags = iota
	ImportUID
	ImportSIG
	ImportSubKey
	ImportSecret
)

type ImportStatus struct {
	Fingerprint string
	Result      error
	Status      ImportStatusFlags
}

type ImportResult struct {
	Considered      int
	NoUserID        int
	Imported        int
	ImportedRSA     int
	Unchanged       int
	NewUserIDs      int
	NewSubKeys      int
	NewSignatures   int
	NewRevocations  int
	SecretRead      int
	SecretImported  int
	SecretUnchanged int
	NotImported     int
	Imports         []ImportStatus
}

func (c *Context) Import(keyData *Data) (*ImportResult, error) {
	return nil, errStub
}

type Key struct {
}

func (k *Key) Release() {
}

func (k *Key) Revoked() bool {
	return false
}

func (k *Key) Expired() bool {
	return false
}

func (k *Key) Disabled() bool {
	return false
}

func (k *Key) Invalid() bool {
	return false
}

func (k *Key) CanEncrypt() bool {
	return false
}

func (k *Key) CanSign() bool {
	return false
}

func (k *Key) CanCertify() bool {
	return false
}

func (k *Key) Secret() bool {
	return false
}

func (k *Key) CanAuthenticate() bool {
	return false
}

func (k *Key) IsQualified() bool {
	return false
}

func (k *Key) Protocol() Protocol {
	return 0
}

func (k *Key) IssuerSerial() string {
	return ""
}

func (k *Key) IssuerName() string {
	return ""
}

func (k *Key) ChainID() string {
	return ""
}

func (k *Key) OwnerTrust() Validity {
	return 0
}

func (k *Key) SubKeys() *SubKey {
	return nil
}

func (k *Key) UserIDs() *UserID {
	return nil
}

func (k *Key) KeyListMode() KeyListMode {
	return 0
}

type SubKey struct {
}

func (k *SubKey) Next() *SubKey {
	return nil
}

func (k *SubKey) Revoked() bool {
	return false
}

func (k *SubKey) Expired() bool {
	return false
}

func (k *SubKey) Disabled() bool {
	return false
}

func (k *SubKey) Invalid() bool {
	return false
}

func (k *SubKey) Secret() bool {
	return false
}

func (k *SubKey) KeyID() string {
	return ""
}

func (k *SubKey) Fingerprint() string {
	return ""
}

func (k *SubKey) Created() time.Time {
	return time.Time{}
}

func (k *SubKey) Expires() time.Time {
	return time.Time{}
}

func (k *SubKey) CardNumber() string {
	return ""
}

type UserID struct {
}

func (u *UserID) Next() *UserID {
	return nil
}

func (u *UserID) Revoked() bool {
	return false
}

func (u *UserID) Invalid() bool {
	return false
}

func (u *UserID) Validity() Validity {
	return 0
}

func (u *UserID) UID() string {
	return ""
}

func (u *UserID) Name() string {
	return ""
}

func (u *UserID) Comment() string {
	return ""
}

func (u *UserID) Email() string {
	return ""
}
