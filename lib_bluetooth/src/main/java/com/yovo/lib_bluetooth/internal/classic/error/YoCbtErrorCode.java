package com.yovo.lib_bluetooth.internal.classic.error;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.error.YoErrorCode;
import com.yovo.lib_bluetooth.model.classic.CbtErrorCode;

/**
 * 经典蓝牙 SDK 按能力域（发现、配对、ACL、Profile、SPP、协议等）划分的完整错误码。
 *
 * <p>每个内部错误码<b>自身携带</b>对外契约分类 {@link CbtErrorCode}：门面投影只读取
 * {@link #publicClass()}，不再维护「内部字符串 → 公开枚举」的对表，也不存在 default 兜底分支。
 * {@link #code()} 只是诊断标识（日志、时间线、traceId 去重），不参与对外分类。</p>
 */
public enum YoCbtErrorCode implements YoErrorCode<CbtErrorCode> {

    // ── GENERAL ──────────────────────────────────────────────────
    YO_CBT_BLUETOOTH_NOT_SUPPORTED(CbtErrorCode.BLUETOOTH_NOT_SUPPORTED),
    YO_CBT_BLUETOOTH_DISABLED(CbtErrorCode.BLUETOOTH_DISABLED),
    YO_CBT_INTERNAL_ERROR(CbtErrorCode.COMMAND_FAILED),

    // ── PERMISSION ───────────────────────────────────────────────
    YO_CBT_PERMISSION_SCAN_DENIED(CbtErrorCode.PERMISSION_DENIED),
    YO_CBT_PERMISSION_CONNECT_DENIED(CbtErrorCode.PERMISSION_DENIED),
    YO_CBT_PERMISSION_LOCATION_DENIED(CbtErrorCode.PERMISSION_DENIED),

    // ── DISCOVERY ────────────────────────────────────────────────
    YO_CBT_DISCOVERY_ALREADY_RUNNING(CbtErrorCode.DISCOVERY_START_FAILED),
    YO_CBT_DISCOVERY_START_FAILED(CbtErrorCode.DISCOVERY_START_FAILED),
    YO_CBT_DISCOVERY_STOP_FAILED(CbtErrorCode.DISCOVERY_START_FAILED),
    YO_CBT_DISCOVERY_TIMEOUT(CbtErrorCode.DISCOVERY_START_FAILED),
    YO_CBT_DISCOVERY_RECEIVER_ERROR(CbtErrorCode.DISCOVERY_START_FAILED),

    // ── PAIRING ──────────────────────────────────────────────────
    YO_CBT_BONDED_DEVICE_NOT_FOUND(CbtErrorCode.BOND_FAILED),
    YO_CBT_BOND_START_FAILED(CbtErrorCode.BOND_FAILED),
    YO_CBT_BOND_CANCEL_FAILED(CbtErrorCode.BOND_FAILED),
    YO_CBT_BOND_REMOVE_FAILED(CbtErrorCode.BOND_FAILED),
    YO_CBT_BOND_HIDDEN_API_BLOCKED(CbtErrorCode.BOND_FAILED),
    YO_CBT_BOND_STATE_UNKNOWN(CbtErrorCode.BOND_FAILED),
    YO_CBT_BOND_DEVICE_NOT_CONNECTABLE(CbtErrorCode.BOND_FAILED),

    // ── ACL LINK ─────────────────────────────────────────────────
    YO_CBT_ACL_STATE_UNKNOWN(CbtErrorCode.ACL_QUERY_FAILED),
    YO_CBT_ACL_STATE_QUERY_FAILED(CbtErrorCode.ACL_QUERY_FAILED),
    YO_CBT_ACL_RECEIVER_ERROR(CbtErrorCode.ACL_QUERY_FAILED),

    // ── SERVICE DISCOVERY (SDP) ──────────────────────────────────
    YO_CBT_SDP_START_FAILED(CbtErrorCode.SDP_FAILED),
    YO_CBT_SDP_TIMEOUT(CbtErrorCode.SDP_FAILED),
    YO_CBT_SDP_UUID_EMPTY(CbtErrorCode.SDP_FAILED),
    YO_CBT_SDP_UUID_NOT_MATCHED(CbtErrorCode.SDP_FAILED),
    YO_CBT_SDP_ACTION_UUID_NOT_RECEIVED(CbtErrorCode.SDP_FAILED),
    YO_CBT_SDP_CHANNEL_NOT_RESOLVED(CbtErrorCode.SDP_FAILED),

    // ── PROFILE ──────────────────────────────────────────────────
    YO_CBT_PROFILE_UNSUPPORTED(CbtErrorCode.PROFILE_PROXY_FAILED),
    YO_CBT_PROFILE_TYPE_UNSUPPORTED(CbtErrorCode.PROFILE_PROXY_FAILED),
    YO_CBT_PROFILE_STATE_QUERY_FAILED(CbtErrorCode.PROFILE_STATE_QUERY_FAILED),
    YO_CBT_PROFILE_CAPABILITY_UNKNOWN(CbtErrorCode.PROFILE_STATE_QUERY_FAILED),

    // ── PROFILE PROXY ────────────────────────────────────────────
    YO_CBT_PROFILE_PROXY_GET_FAILED(CbtErrorCode.PROFILE_PROXY_FAILED),
    YO_CBT_PROFILE_PROXY_NOT_READY(CbtErrorCode.PROFILE_PROXY_FAILED),
    YO_CBT_PROFILE_PROXY_ALREADY_REQUESTED(CbtErrorCode.PROFILE_PROXY_FAILED),
    YO_CBT_PROFILE_PROXY_LIFECYCLE_ERROR(CbtErrorCode.PROFILE_PROXY_FAILED),
    YO_CBT_AUDIO_CONNECT_REJECTED(CbtErrorCode.AUDIO_CONNECT_REJECTED),

    // ── A2DP ─────────────────────────────────────────────────────
    YO_CBT_A2DP_STATE_QUERY_FAILED(CbtErrorCode.PROFILE_STATE_QUERY_FAILED),
    YO_CBT_A2DP_PROXY_NOT_READY(CbtErrorCode.PROFILE_PROXY_FAILED),
    YO_CBT_A2DP_ACTIVE_DEVICE_UNAVAILABLE(CbtErrorCode.PROFILE_STATE_QUERY_FAILED),
    YO_CBT_A2DP_CODEC_STATE_UNAVAILABLE(CbtErrorCode.PROFILE_STATE_QUERY_FAILED),

    // ── HEADSET / HFP ────────────────────────────────────────────
    YO_CBT_HEADSET_STATE_QUERY_FAILED(CbtErrorCode.PROFILE_STATE_QUERY_FAILED),
    YO_CBT_HEADSET_PROXY_NOT_READY(CbtErrorCode.PROFILE_PROXY_FAILED),
    YO_CBT_HEADSET_AUDIO_STATE_UNKNOWN(CbtErrorCode.PROFILE_STATE_QUERY_FAILED),
    YO_CBT_HEADSET_SCO_STATE_UNAVAILABLE(CbtErrorCode.PROFILE_STATE_QUERY_FAILED),

    // ── SPP TRANSPORT ────────────────────────────────────────────
    YO_CBT_SPP_DEVICE_INVALID(CbtErrorCode.SPP_DEVICE_INVALID),
    YO_CBT_SPP_UUID_INVALID(CbtErrorCode.SPP_UUID_INVALID),
    YO_CBT_SPP_NOT_CONNECTED(CbtErrorCode.SPP_CONNECT_FAILED),
    YO_CBT_SPP_ALREADY_CONNECTED(CbtErrorCode.SPP_CONNECT_FAILED),
    YO_CBT_SPP_SOCKET_CREATE_FAILED(CbtErrorCode.SPP_SOCKET_CREATE_FAILED),
    YO_CBT_SPP_CONNECT_FAILED(CbtErrorCode.SPP_CONNECT_FAILED),
    YO_CBT_SPP_CONNECT_TIMEOUT(CbtErrorCode.SPP_CONNECT_TIMEOUT),
    YO_CBT_SPP_STREAM_OPEN_FAILED(CbtErrorCode.SPP_STREAM_OPEN_FAILED),
    YO_CBT_SPP_WRITE_FAILED(CbtErrorCode.SPP_WRITE_FAILED),
    YO_CBT_SPP_READ_FAILED(CbtErrorCode.SPP_READ_FAILED),
    YO_CBT_SPP_REMOTE_CLOSED(CbtErrorCode.SPP_REMOTE_CLOSED),
    YO_CBT_SPP_LOCAL_CLOSED(CbtErrorCode.SPP_LOCAL_CLOSED),
    YO_CBT_SPP_CONNECTION_ID_NOT_FOUND(CbtErrorCode.SPP_CONNECT_FAILED),

    // ── SPP MULTI-CONNECTION ─────────────────────────────────────
    YO_CBT_SPP_CONNECTION_KEY_CONFLICT(CbtErrorCode.SPP_CONNECT_FAILED),
    YO_CBT_SPP_CONNECTION_LIMIT_EXCEEDED(CbtErrorCode.SPP_CONNECT_FAILED),
    YO_CBT_SPP_CONNECTION_GENERATION_EXPIRED(CbtErrorCode.SPP_CONNECT_FAILED),

    // ── SPP CHANNEL ──────────────────────────────────────────────
    YO_CBT_SPP_CHANNEL_INVALID(CbtErrorCode.SPP_CHANNEL_INVALID),
    YO_CBT_SPP_CHANNEL_HIDDEN_API_NOT_AVAILABLE(CbtErrorCode.SPP_CHANNEL_HIDDEN_API_NOT_AVAILABLE),
    YO_CBT_SPP_CHANNEL_HIDDEN_API_BLOCKED(CbtErrorCode.SPP_CHANNEL_HIDDEN_API_BLOCKED),
    YO_CBT_SPP_CHANNEL_REFLECTION_FAILED(CbtErrorCode.SPP_CHANNEL_REFLECTION_FAILED),
    YO_CBT_SPP_CHANNEL_SOCKET_CREATE_FAILED(CbtErrorCode.SPP_CHANNEL_SOCKET_CREATE_FAILED),
    YO_CBT_SPP_CHANNEL_CONNECT_FAILED(CbtErrorCode.SPP_CHANNEL_CONNECT_FAILED),
    YO_CBT_SPP_CHANNEL_UNSUPPORTED_ON_ROM(CbtErrorCode.SPP_CHANNEL_CONNECT_FAILED),
    YO_CBT_SPP_CHANNEL_IN_USE(CbtErrorCode.SPP_CHANNEL_CONNECT_FAILED),

    // ── PROTOCOL ─────────────────────────────────────────────────
    YO_CBT_PACKET_ENCODE_FAILED(CbtErrorCode.COMMAND_FAILED),
    YO_CBT_PACKET_DECODE_FAILED(CbtErrorCode.COMMAND_FAILED),
    YO_CBT_COMMAND_TIMEOUT(CbtErrorCode.COMMAND_FAILED),
    YO_CBT_COMMAND_CANCELLED(CbtErrorCode.COMMAND_FAILED),
    YO_CBT_RESPONSE_NOT_MATCHED(CbtErrorCode.COMMAND_FAILED),
    YO_CBT_COMMAND_QUEUE_FULL(CbtErrorCode.COMMAND_FAILED),

    // ── LIFECYCLE ────────────────────────────────────────────────
    YO_CBT_OPERATION_CANCELLED(CbtErrorCode.OPERATION_CANCELLED),
    YO_CBT_TIMEOUT(CbtErrorCode.COMMAND_FAILED),
    YO_CBT_RELEASED(CbtErrorCode.RELEASED),
    YO_CBT_ALREADY_RELEASED(CbtErrorCode.RELEASED),
    YO_CBT_RELEASE_IN_PROGRESS(CbtErrorCode.RELEASED);

    @NonNull
    private final CbtErrorCode publicClass;

    YoCbtErrorCode(@NonNull CbtErrorCode publicClass) {
        this.publicClass = publicClass;
    }

    /** 对外失败分类（结构化载荷）：门面投影直接读取。 */
    @NonNull
    @Override
    public CbtErrorCode publicClass() {
        return publicClass;
    }

    /** 诊断标识：日志、时间线、按 traceId 去重使用，不参与对外错误分类。 */
    @NonNull
    @Override
    public String code() {
        return name();
    }
}
