package com.yovo.lib_bluetooth.internal.ble.platform;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.yovo.lib_bluetooth.internal.ble.channel.YoBleL2capPlatform;
import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.internal.platform.AndroidPlatform;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.provider.lib_common.log.YoBtLogger;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

/**
 * Android 10+ (API 29+) L2CAP CoC Socket 平台适配器（支持 connect 阻塞期间可取消关闭 Socket）。
 *
 * <p>L2CAP CoC 的全部平台调用都收在本类：能力判定走 {@link AndroidPlatform#supportsL2capCoc()}，
 * 安全/非安全通道的创建分支只在这里出现一次。</p>
 */
@SuppressLint("MissingPermission")
public final class YoBleAndroidL2capPlatform implements YoBleL2capPlatform {

    private final YoBluetoothAdapterProvider adapterProvider;

    public YoBleAndroidL2capPlatform(@NonNull YoBluetoothAdapterProvider adapterProvider) {
        this.adapterProvider = adapterProvider;
    }

    @NonNull
    @Override
    public SocketConnection openClientChannel(@NonNull BleDeviceId deviceId,
                                              int psm,
                                              boolean secure,
                                              @Nullable Consumer<Closeable> cancelHookConsumer)
            throws IOException {
        if (!AndroidPlatform.supportsL2capCoc()) {
            throw new IOException("L2CAP CoC requires API 29+");
        }
        BluetoothAdapter adapter = adapterProvider.adapter();
        if (adapter == null) {
            throw new IOException("BluetoothAdapter unavailable");
        }
        BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(deviceId.value());
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid device address: " + deviceId, e);
        }
        BluetoothSocket socket = createClientChannel(device, psm, secure);
        if (cancelHookConsumer != null) {
            cancelHookConsumer.accept(socket::close);
        }
        InputStream in;
        OutputStream out;
        try {
            socket.connect();
            in = socket.getInputStream();
            out = socket.getOutputStream();
        } catch (Throwable connectFailure) {
            closeQuietly(socket, "L2CAP client channel");
            if (connectFailure instanceof IOException) {
                throw (IOException) connectFailure;
            }
            throw new IOException("L2CAP connect failed: " + connectFailure.getMessage(), connectFailure);
        }

        return new SocketConnection() {
            @NonNull
            @Override
            public BleDeviceId remoteDeviceId() {
                return deviceId;
            }

            @Override
            public int read(@NonNull byte[] buffer) throws IOException {
                return in.read(buffer);
            }

            @Override
            public void write(@NonNull byte[] data) throws IOException {
                out.write(data);
                out.flush();
            }

            @Override
            public void close() throws IOException {
                socket.close();
            }
        };
    }

    @NonNull
    @Override
    public ListeningSocket openServer(boolean secure, @Nullable Consumer<Closeable> cancelHookConsumer)
            throws IOException {
        if (!AndroidPlatform.supportsL2capCoc()) {
            throw new IOException("L2CAP CoC requires API 29+");
        }
        BluetoothAdapter adapter = adapterProvider.adapter();
        if (adapter == null) {
            throw new IOException("BluetoothAdapter unavailable");
        }
        BluetoothServerSocket server = listenServer(adapter, secure);
        if (cancelHookConsumer != null) {
            cancelHookConsumer.accept(server::close);
        }
        return new ListeningSocket() {
            @Override
            public int psm() {
                return server.getPsm();
            }

            @NonNull
            @Override
            public SocketConnection accept() throws IOException {
                BluetoothSocket accepted = server.accept();
                BluetoothDevice remote = accepted.getRemoteDevice();
                BleDeviceId remoteId = new BleDeviceId(remote.getAddress());
                InputStream in = accepted.getInputStream();
                OutputStream out = accepted.getOutputStream();
                return new SocketConnection() {
                    @NonNull
                    @Override
                    public BleDeviceId remoteDeviceId() {
                        return remoteId;
                    }

                    @Override
                    public int read(@NonNull byte[] buffer) throws IOException {
                        return in.read(buffer);
                    }

                    @Override
                    public void write(@NonNull byte[] data) throws IOException {
                        out.write(data);
                        out.flush();
                    }

                    @Override
                    public void close() throws IOException {
                        accepted.close();
                    }
                };
            }

            @Override
            public void close() throws IOException {
                server.close();
            }
        };
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @NonNull
    private static BluetoothSocket createClientChannel(@NonNull BluetoothDevice device,
                                                       int psm,
                                                       boolean secure)
            throws IOException {
        return secure ? device.createL2capChannel(psm) : device.createInsecureL2capChannel(psm);
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @NonNull
    private static BluetoothServerSocket listenServer(@NonNull BluetoothAdapter adapter,
                                                      boolean secure)
            throws IOException {
        return secure
                ? adapter.listenUsingL2capChannel()
                : adapter.listenUsingInsecureL2capChannel();
    }

    /**
     * 连接失败后的清理：错误已按 IOException 上报，清理失败不改变上报的失败类别，
     * 只在日志里留证，避免第二个异常覆盖根因。
     */
    private static void closeQuietly(@NonNull BluetoothSocket socket, @NonNull String what) {
        try {
            socket.close();
        } catch (IOException closeFailure) {
            YoBtLogger.w("YoBleAndroidL2cap", what + " close after failure also failed", closeFailure);
        }
    }
}
