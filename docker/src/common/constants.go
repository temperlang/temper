package common

// All temper created container names start with this
const CONTAINER_LEAD = "temper"

const TT_LOG = "TT_LOG"

// Path within the home directory
const DEFAULT_PID_FILE = ".active-pids"

// Path within the home directory
const DEFAULT_LOG_SOCKET = ".logging-pipe"

// If the runner itself fails, use this code to indicate; see ssh for prior art
const FAIL_CODE = 255
