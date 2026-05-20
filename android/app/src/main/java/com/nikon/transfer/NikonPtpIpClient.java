package com.nikon.transfer;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class NikonPtpIpClient implements Closeable {
    private static final int DEFAULT_TIMEOUT_MS = 10_000;
    private static final int PTPIP_INIT_COMMAND_REQUEST = 1;
    private static final int PTPIP_INIT_COMMAND_ACK = 2;
    private static final int PTPIP_COMMAND_REQUEST = 6;
    private static final int PTPIP_COMMAND_RESPONSE = 7;
    private static final int PTPIP_START_DATA_PACKET = 9;
    private static final int PTPIP_DATA_PACKET = 10;
    private static final int PTPIP_END_DATA_PACKET = 12;

    private static final int OP_OPEN_SESSION = 0x1002;
    private static final int OP_GET_STORAGE_IDS = 0x1004;
    private static final int OP_GET_OBJECT_HANDLES = 0x1007;
    private static final int OP_GET_OBJECT_INFO = 0x1008;
    private static final int OP_GET_OBJECT = 0x1009;
    private static final int OP_GET_THUMB = 0x100A;

    private final String host;
    private final int port;
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;
    private int transactionId = 1;
    private int sessionId = 1;
    private String cameraName = "Nikon Camera";

    NikonPtpIpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    Session connect(String model) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), DEFAULT_TIMEOUT_MS);
        socket.setSoTimeout(DEFAULT_TIMEOUT_MS);
        input = new DataInputStream(socket.getInputStream());
        output = new DataOutputStream(socket.getOutputStream());

        sendInitCommandRequest(model);
        readInitAck();
        command(OP_OPEN_SESSION, new int[]{sessionId});
        return new Session(sessionId, cameraName);
    }

    boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    List<NikonPhoto> listPhotos(Context context) throws IOException {
        List<Integer> handles = readObjectHandles();
        List<NikonPhoto> photos = new ArrayList<>();
        for (Integer handle : handles) {
            ObjectInfo info = readObjectInfo(handle);
            if (!info.isImage()) {
                continue;
            }

            String thumb = "";
            try {
                byte[] thumbBytes = readObjectBytes(OP_GET_THUMB, handle);
                thumb = "data:image/jpeg;base64," + Base64.encodeToString(thumbBytes, Base64.NO_WRAP);
            } catch (IOException ignored) {
                thumb = "";
            }

            photos.add(new NikonPhoto(
                    handle,
                    info.filename,
                    info.captureTime,
                    info.compressedSize,
                    info.width,
                    info.height,
                    info.fileFormat(),
                    thumb
            ));
        }
        return photos;
    }

    int downloadPhotos(Context context, List<Integer> objectHandles, String size) throws IOException {
        int saved = 0;
        for (Integer handle : objectHandles) {
            ObjectInfo info = readObjectInfo(handle);
            byte[] data = readObjectBytes(OP_GET_OBJECT, handle);
            byte[] outputBytes = data;
            String mimeType = info.mimeType();
            String filename = targetFilename(info.filename, size);

            if (!"original".equals(size) && "image/jpeg".equals(mimeType)) {
                outputBytes = resizeJpeg(data, "2mp".equals(size) ? 2_000_000 : 8_000_000);
                filename = filename.replaceAll("\\.[^.]+$", "") + "_" + size.toUpperCase(Locale.US) + ".jpg";
            }

            saveImage(context, filename, mimeType, outputBytes);
            saved += 1;
        }
        return saved;
    }

    private List<Integer> readObjectHandles() throws IOException {
        byte[] storageBytes = readCommandData(OP_GET_STORAGE_IDS, new int[]{});
        List<Integer> storageIds = parseUInt32Array(storageBytes);
        List<Integer> handles = new ArrayList<>();
        for (Integer storageId : storageIds) {
            byte[] handleBytes = readCommandData(OP_GET_OBJECT_HANDLES, new int[]{storageId, 0, 0});
            handles.addAll(parseUInt32Array(handleBytes));
        }
        return handles;
    }

    private ObjectInfo readObjectInfo(int handle) throws IOException {
        byte[] bytes = readCommandData(OP_GET_OBJECT_INFO, new int[]{handle});
        return ObjectInfo.parse(bytes);
    }

    private byte[] readObjectBytes(int opCode, int handle) throws IOException {
        return readCommandData(opCode, new int[]{handle});
    }

    private byte[] readCommandData(int opCode, int[] params) throws IOException {
        int currentTransaction = command(opCode, params);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        while (true) {
            Packet packet = readPacket();
            if (packet.type == PTPIP_START_DATA_PACKET || packet.type == PTPIP_DATA_PACKET || packet.type == PTPIP_END_DATA_PACKET) {
                ByteBuffer buffer = packet.payload();
                int transaction = buffer.getInt();
                if (transaction != currentTransaction) {
                    continue;
                }
                if (packet.type == PTPIP_START_DATA_PACKET && buffer.remaining() >= 8) {
                    buffer.getLong();
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                data.write(chunk);
                if (packet.type == PTPIP_END_DATA_PACKET) {
                    break;
                }
            } else if (packet.type == PTPIP_COMMAND_RESPONSE) {
                break;
            }
        }
        return data.toByteArray();
    }

    private int command(int opCode, int[] params) throws IOException {
        int currentTransaction = transactionId++;
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeInt(payload, PTPIP_COMMAND_REQUEST);
        writeInt(payload, 1);
        writeShort(payload, opCode);
        writeInt(payload, currentTransaction);
        for (int param : params) {
            writeInt(payload, param);
        }
        writePacket(payload.toByteArray());
        return currentTransaction;
    }

    private void sendInitCommandRequest(String model) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeInt(payload, PTPIP_INIT_COMMAND_REQUEST);
        writeGuid(payload);
        writeUtf16(payload, "Nikon Transfer Android");
        writeInt(payload, 0x00010000);
        writePacket(payload.toByteArray());
        cameraName = "Z5II".equals(model) ? "Nikon Z 5II" : "Nikon Z 30";
    }

    private void readInitAck() throws IOException {
        Packet packet = readPacket();
        if (packet.type != PTPIP_INIT_COMMAND_ACK) {
            throw new IOException("Unexpected PTP/IP init response: " + packet.type);
        }
    }

    private Packet readPacket() throws IOException {
        int length = readLittleEndianInt(input);
        if (length < 8 || length > 128 * 1024 * 1024) {
            throw new IOException("Invalid PTP/IP packet length: " + length);
        }
        int type = readLittleEndianInt(input);
        byte[] body = new byte[length - 8];
        input.readFully(body);
        return new Packet(type, body);
    }

    private void writePacket(byte[] bodyWithType) throws IOException {
        writeInt(output, bodyWithType.length + 4);
        output.write(bodyWithType);
        output.flush();
    }

    private static byte[] resizeJpeg(byte[] data, int targetPixels) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (bitmap == null || bitmap.getWidth() * bitmap.getHeight() <= targetPixels) {
            return data;
        }

        double scale = Math.sqrt(targetPixels / (double) (bitmap.getWidth() * bitmap.getHeight()));
        int width = Math.max(1, (int) Math.round(bitmap.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(bitmap.getHeight() * scale));
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, width, height, true);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        resized.compress(Bitmap.CompressFormat.JPEG, 92, output);
        return output.toByteArray();
    }

    private static void saveImage(Context context, String filename, String mimeType, byte[] bytes) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
        values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/尼康图传");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("MediaStore insert failed");
        }

        try (OutputStream stream = resolver.openOutputStream(uri)) {
            if (stream == null) {
                throw new IOException("MediaStore output stream failed");
            }
            stream.write(bytes);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
        }
    }

    private static String targetFilename(String filename, String size) {
        if ("original".equals(size)) {
            return filename;
        }
        return filename;
    }

    private static List<Integer> parseUInt32Array(byte[] bytes) {
        List<Integer> values = new ArrayList<>();
        if (bytes.length < 4) {
            return values;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        long count = Integer.toUnsignedLong(buffer.getInt());
        for (int index = 0; index < count && buffer.remaining() >= 4; index++) {
            values.add(buffer.getInt());
        }
        return values;
    }

    private static int readLittleEndianInt(DataInputStream input) throws IOException {
        byte[] bytes = new byte[4];
        input.readFully(bytes);
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static void writeGuid(OutputStream output) throws IOException {
        byte[] guid = new byte[]{0x4e, 0x49, 0x4b, 0x4f, 0x4e, 0x54, 0x52, 0x41, 0x4e, 0x53, 0x46, 0x45, 0x52, 0x30, 0x31, 0x00};
        output.write(guid);
    }

    private static void writeUtf16(OutputStream output, String value) throws IOException {
        for (int index = 0; index < value.length(); index++) {
            writeShort(output, value.charAt(index));
        }
        writeShort(output, 0);
    }

    private static void writeInt(OutputStream output, int value) throws IOException {
        output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());
    }

    private static void writeShort(OutputStream output, int value) throws IOException {
        output.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) value).array());
    }

    @Override
    public void close() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    static final class Session {
        final int sessionId;
        final String cameraName;

        Session(int sessionId, String cameraName) {
            this.sessionId = sessionId;
            this.cameraName = cameraName;
        }
    }

    private static final class Packet {
        final int type;
        final byte[] body;

        Packet(int type, byte[] body) {
            this.type = type;
            this.body = body;
        }

        ByteBuffer payload() {
            return ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        }
    }

    private static final class ObjectInfo {
        final int objectFormat;
        final long compressedSize;
        final int width;
        final int height;
        final String filename;
        final String captureTime;

        ObjectInfo(int objectFormat, long compressedSize, int width, int height, String filename, String captureTime) {
            this.objectFormat = objectFormat;
            this.compressedSize = compressedSize;
            this.width = width;
            this.height = height;
            this.filename = filename;
            this.captureTime = captureTime;
        }

        static ObjectInfo parse(byte[] bytes) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (buffer.remaining() < 52) {
                return new ObjectInfo(0x3801, bytes.length, 0, 0, "DSC_UNKNOWN.JPG", OffsetDateTime.now().toString());
            }

            buffer.getInt();
            int format = Short.toUnsignedInt(buffer.getShort());
            buffer.getShort();
            long size = Integer.toUnsignedLong(buffer.getInt());
            buffer.getShort();
            buffer.getInt();
            buffer.getInt();
            buffer.getInt();
            int width = buffer.getInt();
            int height = buffer.getInt();
            buffer.getInt();
            buffer.getInt();
            buffer.getInt();
            buffer.getInt();
            buffer.getInt();
            buffer.getShort();
            String filename = readPtpString(buffer, "DSC_UNKNOWN.JPG");
            String capture = readPtpString(buffer, OffsetDateTime.now().toString());
            return new ObjectInfo(format, size, width, height, filename, capture);
        }

        boolean isImage() {
            return objectFormat == 0x3801 || objectFormat == 0x3800 || objectFormat == 0x380B || objectFormat == 0xB103;
        }

        String fileFormat() {
            String lower = filename.toLowerCase(Locale.US);
            if (lower.endsWith(".nef")) {
                return "NEF";
            }
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                return "JPG";
            }
            return "OTHER";
        }

        String mimeType() {
            if ("NEF".equals(fileFormat())) {
                return "image/x-nikon-nef";
            }
            return "image/jpeg";
        }

        private static String readPtpString(ByteBuffer buffer, String fallback) {
            if (!buffer.hasRemaining()) {
                return fallback;
            }
            int length = Byte.toUnsignedInt(buffer.get());
            if (length == 0 || buffer.remaining() < length * 2) {
                return fallback;
            }
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < length - 1; index++) {
                builder.append(buffer.getChar());
            }
            if (buffer.remaining() >= 2) {
                buffer.getChar();
            }
            return builder.length() == 0 ? fallback : builder.toString();
        }
    }
}
