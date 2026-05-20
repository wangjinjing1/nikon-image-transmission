package com.nikon.transfer;

import android.Manifest;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.text.format.Formatter;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@CapacitorPlugin(
        name = "NikonCamera",
        permissions = {
                @Permission(strings = { Manifest.permission.WRITE_EXTERNAL_STORAGE }, alias = "storage")
        }
)
public class NikonCameraPlugin extends Plugin {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private NikonPtpIpClient client;
    private static final int STA_SCAN_TIMEOUT_MS = 350;
    private static final int STA_SCAN_THREADS = 24;

    @PluginMethod
    public void connect(PluginCall call) {
        String model = call.getString("model", "Z30");
        String mode = call.getString("mode", "ap");
        String host = call.getString("host", "");
        int port = call.getInt("port", 15740);

        executor.execute(() -> {
            try {
                String manualHost = host == null ? "" : host.trim();
                String targetHost = detectTargetAddress(mode, manualHost, port);
                if (targetHost.isEmpty()) {
                    throw new IllegalStateException(discoveryFailedMessage(mode));
                }

                NikonPtpIpClient.Session session;
                try {
                    client = new NikonPtpIpClient(targetHost, port);
                    session = client.connect(model);
                } catch (Exception firstException) {
                    closeClient();
                    String fallbackHost = "";
                    if (!manualHost.isEmpty() || isConnectionRefused(firstException)) {
                        fallbackHost = discoverCameraAddress(port);
                    }
                    if (fallbackHost.isEmpty() || fallbackHost.equals(targetHost)) {
                        throw firstException;
                    }
                    targetHost = fallbackHost;
                    client = new NikonPtpIpClient(targetHost, port);
                    session = client.connect(model);
                }

                JSObject result = new JSObject();
                result.put("connected", true);
                result.put("model", model);
                result.put("mode", mode);
                result.put("host", targetHost);
                result.put("cameraName", session.cameraName);
                result.put("serialNumber", session.serialNumber);
                result.put("sessionId", session.sessionId);
                call.resolve(result);
            } catch (Exception exception) {
                call.reject(connectErrorMessage(mode, exception), exception);
            }
        });
    }

    @PluginMethod
    public void requestStoragePermission(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || getPermissionState("storage") == PermissionState.GRANTED) {
            JSObject result = new JSObject();
            result.put("storage", "granted");
            call.resolve(result);
            return;
        }

        requestPermissionForAlias("storage", call, "checkPermissions");
    }

    @PluginMethod
    public void listPhotos(PluginCall call) {
        executor.execute(() -> {
            try {
                ensureClient();
                List<NikonPhoto> photos = client.listPhotos(getContext());
                JSArray items = new JSArray();
                for (NikonPhoto photo : photos) {
                    items.put(photo.toJson());
                }

                JSObject result = new JSObject();
                result.put("photos", items);
                call.resolve(result);
            } catch (Exception exception) {
                call.reject("读取相机照片失败", exception);
            }
        });
    }

    @PluginMethod
    public void downloadPhotos(PluginCall call) {
        JSArray handles = call.getArray("objectHandles", new JSArray());
        String size = call.getString("size", "original");
        String albumName = sanitizeAlbumName(call.getString("albumName", "尼康图传"));

        executor.execute(() -> {
            try {
                ensureClient();
                List<Integer> objectHandles = new ArrayList<>();
                for (int index = 0; index < handles.length(); index++) {
                    objectHandles.add(handles.getInt(index));
                }

                int saved = client.downloadPhotos(getContext(), objectHandles, size, albumName);
                JSObject result = new JSObject();
                result.put("saved", saved);
                result.put("albumName", albumName);
                call.resolve(result);
            } catch (Exception exception) {
                call.reject("下载照片失败", exception);
            }
        });
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        executor.execute(() -> {
            if (client != null) {
                closeClient();
            }
            JSObject result = new JSObject();
            result.put("connected", false);
            call.resolve(result);
        });
    }

    private void ensureClient() {
        if (client == null || !client.isConnected()) {
            throw new IllegalStateException("相机未连接");
        }
    }

    private String sanitizeAlbumName(String value) {
        String cleaned = value == null ? "" : value.replaceAll("[\\\\/:*?\"<>|]", "").trim();
        return cleaned.isEmpty() ? "尼康图传" : cleaned;
    }

    private String detectTargetAddress(String mode, String host, int port) {
        if (host != null && !host.isEmpty()) {
            return host;
        }

        String discoveredHost = discoverCameraAddress(port);
        if (!discoveredHost.isEmpty()) {
            return discoveredHost;
        }

        return detectGatewayAddress();
    }

    private void closeClient() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    private String detectGatewayAddress() {
        WifiManager wifiManager = (WifiManager) getContext().getApplicationContext().getSystemService(android.content.Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return "";
        }

        DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
        if (dhcpInfo == null || dhcpInfo.gateway == 0) {
            return "";
        }

        return Formatter.formatIpAddress(dhcpInfo.gateway);
    }

    private String discoverCameraAddress(int port) {
        WifiManager wifiManager = (WifiManager) getContext().getApplicationContext().getSystemService(android.content.Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return "";
        }

        List<String> candidates = new ArrayList<>();
        DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
        if (dhcpInfo != null && dhcpInfo.ipAddress != 0) {
            int network = dhcpInfo.netmask == 0 ? dhcpInfo.ipAddress & 0x00FFFFFF : dhcpInfo.ipAddress & dhcpInfo.netmask;
            int ownIp = dhcpInfo.ipAddress;
            int gateway = dhcpInfo.gateway;
            candidates.addAll(buildScanCandidates(network, ownIp, gateway));
        }

        candidates.addAll(buildInterfaceScanCandidates());
        if (candidates.isEmpty()) {
            return "";
        }

        ExecutorService scanExecutor = Executors.newFixedThreadPool(STA_SCAN_THREADS);
        CompletionService<String> completionService = new ExecutorCompletionService<>(scanExecutor);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (String candidate : candidates) {
                futures.add(completionService.submit(new PortCheck(candidate, port)));
            }

            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos((long) STA_SCAN_TIMEOUT_MS * Math.max(1, candidates.size() / STA_SCAN_THREADS + 1));
            for (int index = 0; index < candidates.size(); index++) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }

                Future<String> future = completionService.poll(remaining, TimeUnit.NANOSECONDS);
                if (future == null) {
                    break;
                }

                String address = future.get();
                if (!address.isEmpty()) {
                    return address;
                }
            }
        } catch (Exception ignored) {
            return "";
        } finally {
            for (Future<String> future : futures) {
                future.cancel(true);
            }
            scanExecutor.shutdownNow();
        }

        return "";
    }

    private boolean isConnectionRefused(Exception exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConnectException) {
                return true;
            }
            current = current.getCause();
        }
        String message = exception.getMessage();
        return message != null && message.toLowerCase().contains("connection refused");
    }

    private String discoveryFailedMessage(String mode) {
        if ("sta".equals(mode)) {
            return "未发现相机服务。请确认相机已连接手机热点或同一 Wi-Fi，并在相机菜单中启用“连接至智能设备/PC 传输”后再试。";
        }
        return "未发现相机服务。请确认手机已连接相机 Wi-Fi 热点，并在相机无线连接菜单中启用连接。";
    }

    private String connectErrorMessage(String mode, Exception exception) {
        if (isConnectionRefused(exception)) {
            return "已找到网络地址，但相机拒绝连接 PTP/IP 服务。请在相机菜单中启用无线传输/连接至智能设备，保持相机停留在等待连接界面后重试。";
        }
        if (exception instanceof SocketTimeoutException) {
            return "连接相机超时。请确认手机和相机在同一网络，且相机无线传输服务已开启。";
        }
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty() || message.contains("failed to connect to /")) {
            return discoveryFailedMessage(mode);
        }
        return message;
    }

    private List<String> buildScanCandidates(int network, int ownIp, int gateway) {
        Set<String> candidates = new LinkedHashSet<>();
        addSubnetCandidates(candidates, network, ownIp, gateway);
        return new ArrayList<>(candidates);
    }

    private List<String> buildInterfaceScanCandidates() {
        Set<String> candidates = new LinkedHashSet<>();
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }

                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress() || !address.isSiteLocalAddress()) {
                        continue;
                    }

                    int ownIp = inetAddressToAndroidInt((Inet4Address) address);
                    addSubnetCandidates(candidates, ownIp & 0x00FFFFFF, ownIp, 0);
                }
            }
        } catch (Exception ignored) {
            return new ArrayList<>(candidates);
        }

        return new ArrayList<>(candidates);
    }

    private void addSubnetCandidates(Set<String> candidates, int network, int ownIp, int gateway) {
        int base = network & 0x00FFFFFF;
        for (int lastOctet = 1; lastOctet <= 254; lastOctet++) {
            int candidate = base | (lastOctet << 24);
            if (candidate == ownIp || candidate == gateway) {
                continue;
            }
            candidates.add(Formatter.formatIpAddress(candidate));
        }
    }

    private int inetAddressToAndroidInt(Inet4Address address) {
        byte[] bytes = address.getAddress();
        return (bytes[0] & 0xFF)
                | ((bytes[1] & 0xFF) << 8)
                | ((bytes[2] & 0xFF) << 16)
                | ((bytes[3] & 0xFF) << 24);
    }

    private static final class PortCheck implements Callable<String> {
        private final String address;
        private final int port;

        PortCheck(String address, int port) {
            this.address = address;
            this.port = port;
        }

        @Override
        public String call() {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(address, port), STA_SCAN_TIMEOUT_MS);
                return address;
            } catch (Exception ignored) {
                return "";
            }
        }
    }
}
