package com.lingmoyun.mdns.handlers;


public interface DiscoveryRunningHandler {

    void onDiscoveryStarted();

    void onDiscoveryStopped();
}
