package com.miniredis.command;

import com.miniredis.protocol.RespValue;
import com.miniredis.storage.*;

import java.util.*;

/**
 * Dispatches incoming parsed RESP command arrays to the storage engine
 * and maps results and domain exceptions to standard RESP2 replies.
 */
