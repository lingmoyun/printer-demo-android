package com.lingmoyun.mdns.handlers;

import java.util.Map;

public interface ServiceDiscoveredHandler {

    void onServiceDiscovered(Map<String, Object> serviceInfoMap);
}