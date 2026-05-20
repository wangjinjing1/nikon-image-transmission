package com.nikon.transfer;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(name = "NikonCamera")
public class NikonCameraPlugin extends Plugin {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private NikonPtpIpClient client;

    @PluginMethod
    public void connect(PluginCall call) {
        String model = call.getString("model", "Z30");
        String mode = call.getString("mode", "ap");
        String host = call.getString("host", "192.168.1.1");
        int port = call.getInt("port", 15740);

        executor.execute(() -> {
            try {
                client = new NikonPtpIpClient(host, port);
                NikonPtpIpClient.Session session = client.connect(model);

                JSObject result = new JSObject();
                result.put("connected", true);
                result.put("model", model);
                result.put("mode", mode);
                result.put("host", host);
                result.put("cameraName", session.cameraName);
                result.put("sessionId", session.sessionId);
                call.resolve(result);
            } catch (Exception exception) {
                call.reject("无法连接相机，请确认手机已连接相机 Wi-Fi，地址为 " + host, exception);
            }
        });
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

        executor.execute(() -> {
            try {
                ensureClient();
                List<Integer> objectHandles = new ArrayList<>();
                for (int index = 0; index < handles.length(); index++) {
                    objectHandles.add(handles.getInt(index));
                }

                int saved = client.downloadPhotos(getContext(), objectHandles, size);
                JSObject result = new JSObject();
                result.put("saved", saved);
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
}
