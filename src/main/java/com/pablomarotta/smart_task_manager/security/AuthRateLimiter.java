package com.pablomarotta.smart_task_manager.security;

import java.util.Collection;

public interface AuthRateLimiter {

    void check(AuthRateLimitScope scope, String remoteAddress, Collection<String> identities);
}
