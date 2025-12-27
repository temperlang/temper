package oci

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"regexp"
	"strings"

	ref "github.com/docker/distribution/reference"
	treg "github.com/docker/docker/api/types/registry"
	log "github.com/sirupsen/logrus"

	"github.com/temperlang/temper-docker/common"
	"github.com/temperlang/temper-docker/xdg"
)

var errNoHostName = errors.New("found no hostname in image reference")
var errNoAuthForHost = errors.New("found no auth for image reference")

func dkrLoadAuth(imageName string) (auth string, err error) {
	hostName, err := hostnameFromImageReference(imageName)
	if err != nil {
		return
	}
	path, err := dotDockerPath()
	if err != nil {
		return
	}
	log.Tracef("Reading auth from %s", path)
	cfg := dkrConfigFile{}
	err = common.LoadJson(path, &cfg)
	if err != nil {
		return
	}
	auth, err = cfg.getAuth(hostName)
	return
}

func dotDockerPath() (out []string, err error) {
	out = make([]string, 0, 1)
	path, err := xdg.Home(".docker/config.json")
	if err != nil {
		return
	}
	out = append(out, path)
	return
}

type dkrConfigFile struct {
	CredsStore  string                     `json:"credsStore"`
	AuthConfigs map[string]treg.AuthConfig `json:"auths"`
}

var validCredsStore = regexp.MustCompile("^[a-z]+$")

// Format used by docker-credentials-helpers
type dkrCredentials struct {
	ServerURL string
	Username  string
	Secret    string
}

const dkrCredProgram = "docker-credential-"

func (cfg dkrConfigFile) getAuth(hostname string) (string, error) {
	auth, ok := searchForAuth(cfg.AuthConfigs, hostname)
	if ok && (auth.Auth != "" || auth.Username != "" || auth.Password != "") {
		log.Tracef("For %s found auth for user '%s'", hostname, auth.Username)
		return treg.EncodeAuthConfig(auth)
	}
	if !validCredsStore.MatchString(cfg.CredsStore) {
		return "", fmt.Errorf("credsStore %s doesn't look valid", cfg.CredsStore)
	}
	// Try using the credentials helper.
	outData, err := common.RunCommandWithStdin(hostname, dkrCredProgram+cfg.CredsStore, "get")
	if err != nil {
		return "", err
	}
	creds := dkrCredentials{}
	err = json.Unmarshal(outData, &creds)
	if err != nil {
		return "", err
	}
	auth = treg.AuthConfig{
		Username:      creds.Username,
		ServerAddress: creds.ServerURL,
		Password:      creds.Secret,
	}
	log.Tracef("Using %s%s found user '%s'", dkrCredProgram, cfg.CredsStore, creds.Username)
	return treg.EncodeAuthConfig(auth)
}

// Load the authorization for the hostname of an image.
func podLoadAuth(imageName string) (out UserPass, err error) {
	hostName, err := hostnameFromImageReference(imageName)
	if err != nil {
		return
	}
	paths, err := podmanAuthPaths()
	if err != nil {
		return
	}
	rec := &podmanAuth{}
	err = common.LoadJson(paths, rec)
	if err != nil {
		return
	}
	authRec, found := rec.Auths[hostName]
	if !found {
		err = errNoAuthForHost
		return
	}
	auth, err := base64.StdEncoding.DecodeString(authRec.Auth)
	if err != nil {
		err = fmt.Errorf("failure to unpack auth in %v: %w", paths, err)
		return
	}
	pair := strings.SplitN(string(auth), ":", 2)
	if len(pair) != 2 {
		err = fmt.Errorf("failure to unpack auth in %v: %w", paths, err)
		return
	}
	return UserPass{pair[0], pair[1]}, nil
}

type podmanAuth struct {
	Auths map[string]struct {
		Auth string `json:"auth"`
	} `json:"auths"`
}

func podmanAuthPaths() ([]string, error) {
	paths := make([]string, 0, 4)
	errs := make([]error, 0, 2)
	registryAuth := os.Getenv("REGISTRY_AUTH_FILE")
	if registryAuth != "" {
		return append(paths, registryAuth), nil
	}
	path, err := xdg.Config("containers/auth.json")
	if err != nil {
		errs = append(errs, err)
	} else {
		paths = append(paths, path)
	}
	path, err = xdg.RuntimeHome("containers/auth.json")
	if err != nil {
		errs = append(errs, err)
	} else {
		paths = append(paths, path)
	}
	if len(paths) == 0 {
		return nil, errors.Join(errs...)
	}
	return paths, nil
}

func hostnameFromImageReference(imageRef string) (string, error) {
	parsed, err := ref.ParseNamed(imageRef)
	if err != nil {
		return "", err
	}
	hostName := ref.Domain(parsed)
	if err != nil {
		return "", err
	}
	if hostName == "" {
		return "", errNoHostName
	}
	return hostName, nil
}

func searchForAuth[T any](auths map[string]T, hostname string) (T, bool) {
	auth, ok := auths[hostname]
	if ok {
		return auth, true
	}
	// Check for legacy hostnames
	for url, auth := range auths {
		if convertToHostname(url) == hostname {
			return auth, true
		}
	}
	var zero T
	return zero, false
}

// ConvertToHostname converts a registry url which has http|https prepended
// to just an hostname.
// Copied from github.com/docker/docker/registry.ConvertToHostname to reduce dependencies.
func convertToHostname(url string) string {
	stripped := url
	if strings.HasPrefix(url, "http://") {
		stripped = strings.TrimPrefix(url, "http://")
	} else if strings.HasPrefix(url, "https://") {
		stripped = strings.TrimPrefix(url, "https://")
	}

	hostName, _, _ := strings.Cut(stripped, "/")
	return hostName
}
