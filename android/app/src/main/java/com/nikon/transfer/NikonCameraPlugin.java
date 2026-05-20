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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(
        name = "NikonCamera",
        permissions = {
                @Permission(strings = { Manifest.permission.WRITE_EXTERNAL_STORAGE }, alias = "storage")
        }
)
public class NikonCameraPlugin extends Plugin {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private NikonPtpIpClient client;

    @PluginMethod
    public void connect(PluginCall call) {
        String model = call.getString("model", "Z30");
        String mode = call.getString("mode", "ap");
        String host = call.getString("host", "192.168.1.1");
        String wifiPassword = call.getString("wifiPassword", "");
        int port = call.getInt("port", 15740);

        executor.execute(() -> {
            try {
                String targetHost = host == null || host.trim().isEmpty() ? detectGatewayAddress() : host.trim();
                if (targetHost.isEmpty()) {
                    throw new IllegalStateException("AP 模式未检测到相机热点网关，请确认手机已连接相机 Wi-Fi。");
                }

                client = new NikonPtpIpClient(targetHost, port);
                NikonPtpIpClient.Session session = client.connect(model);

                JSObject result = new JSObject();
                result.put("connected", true);
                result.put("model", model);
                result.put("mode", mode);
                result.put("host", targetHost);
                result.put("cameraName", session.cameraName);
                result.put("sessionId", session.sessionId);
                call.resolve(result);
            } catch (Exception exception) {
                call.reject("无法连接相机，请确认手机已连接相机 Wi-Fi。", exception);
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
                client.close();
                client = null;
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
}
