package com.nikon.transfer;

import com.getcapacitor.JSObject;

final class NikonPhoto {
    final int objectHandle;
    final String name;
    final String takenAt;
    final long sizeBytes;
    final int width;
    final int height;
    final String format;
    final String thumbnailUrl;

    NikonPhoto(
            int objectHandle,
            String name,
            String takenAt,
            long sizeBytes,
            int width,
            int height,
            String format,
            String thumbnailUrl
    ) {
        this.objectHandle = objectHandle;
        this.name = name;
        this.takenAt = takenAt;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
        this.format = format;
        this.thumbnailUrl = thumbnailUrl;
    }

    JSObject toJson() {
        JSObject json = new JSObject();
        json.put("id", String.valueOf(objectHandle));
        json.put("objectHandle", objectHandle);
        json.put("name", name);
        json.put("takenAt", takenAt);
        json.put("sizeBytes", sizeBytes);
        json.put("width", width);
        json.put("height", height);
        json.put("format", format);
        json.put("thumbnailUrl", thumbnailUrl);
        json.put("isRaw", "NEF".equals(format));
        return json;
    }
}
