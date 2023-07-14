package com.lingmoyun.mdns;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import com.lingmoyun.mdns.handlers.DiscoveryRunningHandler;
import com.lingmoyun.mdns.handlers.ServiceDiscoveredHandler;
import com.lingmoyun.mdns.handlers.ServiceResolvedHandler;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * MdnsPlugin
 * 局域网发现
 *
 * @author guoweifeng
 * @date 2021/8/3
 */
public class MdnsPlugin {
    private final String TAG = getClass().getSimpleName();

    private Context mContext;
    private NsdManager mNsdManager;
    private NsdManager.DiscoveryListener mDiscoveryListener;


    private DiscoveryRunningHandler mDiscoveryRunningHandler;
    private ServiceDiscoveredHandler mDiscoveredHandler;
    private ServiceResolvedHandler mResolvedHandler;

    public MdnsPlugin(Context context) {
        this.mContext = context;
        mDiscoveryRunningHandler = new DiscoveryRunningHandler() {
            @Override
            public void onDiscoveryStarted() {

            }

            @Override
            public void onDiscoveryStopped() {

            }
        };
        mDiscoveredHandler = serviceInfoMap -> {
        };
        mResolvedHandler = serviceInfoMap -> {
        };
    }

    public void startDiscovery(String serviceType) {
        _startDiscovery(serviceType);
    }

    private void _startDiscovery(String serviceName) {

        mNsdManager = (NsdManager) mContext.getSystemService(Context.NSD_SERVICE);

        mDiscoveryListener = new NsdManager.DiscoveryListener() {

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, String.format(Locale.US,
                        "Discovery failed to start on %s with error : %d", serviceType, errorCode));
                mDiscoveryRunningHandler.onDiscoveryStopped();
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, String.format(Locale.US,
                        "Discovery failed to stop on %s with error : %d", serviceType, errorCode));
                mDiscoveryRunningHandler.onDiscoveryStarted();
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.d(TAG, "Started discovery for : " + serviceType);
                mDiscoveryRunningHandler.onDiscoveryStarted();
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "Stopped discovery for : " + serviceType);
                mDiscoveryRunningHandler.onDiscoveryStopped();
            }

            @Override
            public void onServiceFound(NsdServiceInfo nsdServiceInfo) {
                Log.d(TAG, "Found Service : " + nsdServiceInfo.toString());
                mDiscoveredHandler.onServiceDiscovered(ServiceToMap(nsdServiceInfo));

                mNsdManager.resolveService(nsdServiceInfo, new NsdManager.ResolveListener() {
                    @Override
                    public void onResolveFailed(NsdServiceInfo nsdServiceInfo, int i) {
                        Log.d(TAG, "Failed to resolve service : " + nsdServiceInfo.toString());
                    }

                    @Override
                    public void onServiceResolved(NsdServiceInfo nsdServiceInfo) {
                        mResolvedHandler.onServiceResolved(ServiceToMap(nsdServiceInfo));
                    }
                });
            }

            @Override
            public void onServiceLost(NsdServiceInfo nsdServiceInfo) {
                Log.d(TAG, "Lost Service : " + nsdServiceInfo.toString());
            }
        };

        mNsdManager.discoverServices(serviceName, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    public void stopDiscovery() {
        if (mNsdManager != null && mDiscoveryListener != null) {
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
        }
    }

    /**
     * serviceToMap converts an NsdServiceInfo object into a map of relevant info
     * The map can be interpreted by the StandardMessageCodec of Flutter and makes sending data back and forth simpler.
     *
     * @param info The ServiceInfo to convert
     * @return The map that can be interpreted by Flutter and sent back on an EventChannel
     */
    private static Map<String, Object> ServiceToMap(NsdServiceInfo info) {
        Map<String, Object> map = new HashMap<>();

        map.put("name", info.getServiceName() != null ? info.getServiceName() : "");

        map.put("type", info.getServiceType() != null ? info.getServiceType() : "");

        map.put("host", info.getHost() != null ? info.getHost().toString() : "");

        map.put("port", info.getPort());

        return map;
    }

    public DiscoveryRunningHandler getDiscoveryRunningHandler() {
        return mDiscoveryRunningHandler;
    }

    public void setDiscoveryRunningHandler(DiscoveryRunningHandler discoveryRunningHandler) {
        this.mDiscoveryRunningHandler = discoveryRunningHandler;
    }

    public ServiceDiscoveredHandler getDiscoveredHandler() {
        return mDiscoveredHandler;
    }

    public void setDiscoveredHandler(ServiceDiscoveredHandler discoveredHandler) {
        this.mDiscoveredHandler = discoveredHandler;
    }

    public ServiceResolvedHandler getResolvedHandler() {
        return mResolvedHandler;
    }

    public void setResolvedHandler(ServiceResolvedHandler resolvedHandler) {
        this.mResolvedHandler = resolvedHandler;
    }

}
