package com.yovo.lib_ai.wiring;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.internal.common.diagnostics.YoAiDiagnosticsInstaller;
import com.yovo.lib_ai.internal.common.diagnostics.YoAiLogger;

/**
 * lib_ai 杩愯鏃跺畨瑁呭叆鍙ｏ紙鍏紑锛夛細SDK 浣跨敤鍓嶈皟鐢ㄤ竴娆★紝缁熶竴瀹屾垚
 * 鈶?YoLog 鍏滃簳鍒濆鍖栵紙鏈垵濮嬪寲鎵嶅垵濮嬪寲锛?鈶?瀹夎鍏紑璇婃柇妗ュ埌鍐呴儴璇婃柇鎬荤嚎/鏃ュ織闂ㄩ潰銆? *
 * <p>骞傜瓑銆佸彲閲嶅璋冪敤銆侫pp 鐨?{@code provider/lib_ai} 鍦ㄨ閰嶆牴鏋勯€犳椂璋冪敤鏈柟娉曪紝
 * 浠庤€?realtime / speech 绛夌嫭绔嬭矾寰勪篃鑳借惤鏃ュ織锛堜笉鍙緷璧?{@code YoAiAssembly}锛夈€?/p>
 */
public final class YoAiRuntime {

    private YoAiRuntime() {
    }

    public static void install(@NonNull Context context) {
        YoAiLogger.requireInitialized();
        YoAiDiagnosticsInstaller.installSink();
    }
}
