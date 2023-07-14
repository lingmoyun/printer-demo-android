package com.lingmoyun.mdns.handlers;

import java.util.Map;

public interface ServiceResolvedHandler {

    void onServiceResolved(Map<String, Object> serviceInfoMap);
}
