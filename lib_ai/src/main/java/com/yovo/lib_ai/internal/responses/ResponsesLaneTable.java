package com.yovo.lib_ai.internal.responses;

import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiException;
import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.responses.YoResponseObject;
import com.yovo.lib_ai.responses.YoResponseStreamEvent;
import com.yovo.lib_ai.responses.YoResponseStreamListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一条 WebSocket 上各 stream_id 的占用状态。
 * 同一通道在本轮结束前拒绝下一次 create。steered 的 incomplete 不释放通道。
 */
public final class ResponsesLaneTable {

    private final Map<String, Lane> lanes = new ConcurrentHashMap<>();

    public void begin(String laneKey, YoResponseStreamListener listener) {
        Lane lane = lanes.computeIfAbsent(laneKey, key -> new Lane());
        synchronized (lane) {
            if (lane.open) {
                throw new IllegalStateException("Responses stream is busy: " + display(laneKey));
            }
            lane.listener = listener;
            lane.reducer = new ResponsesReducer();
            lane.open = true;
        }
    }

    public void cancel(String laneKey) {
        Lane lane = lanes.get(laneKey);
        if (lane == null) {
            return;
        }
        synchronized (lane) {
            lane.open = false;
            lane.listener = null;
            lane.reducer = new ResponsesReducer();
        }
    }

    public boolean isOpen(String laneKey) {
        Lane lane = lanes.get(laneKey);
        if (lane == null) {
            return false;
        }
        synchronized (lane) {
            return lane.open;
        }
    }

    @Nullable
    public ResponsesDispatch push(String laneKey, JSONObject event) {
        Lane lane = lanes.get(laneKey);
        if (lane == null) {
            return null;
        }
        YoResponseStreamListener listener;
        ResponsesReducer reducer;
        ResponsesTurn.Kind kind;
        synchronized (lane) {
            if (!lane.open || lane.listener == null) {
                return null;
            }
            listener = lane.listener;
            reducer = lane.reducer;
            try {
                reducer.apply(event);
            } catch (JSONException exception) {
                lane.open = false;
                lane.listener = null;
                lane.reducer = new ResponsesReducer();
                return ResponsesDispatch.failure(
                        listener,
                        ResponsesCodec.decodeEvent(event),
                        new YoAiProtocolException(exception.getMessage(), event.toString(), exception));
            }
            kind = ResponsesTurn.classify(event);
            YoResponseStreamEvent decoded = ResponsesCodec.decodeEvent(event);
            if (kind == ResponsesTurn.Kind.FAIL) {
                YoAiProtocolException failure = new YoAiProtocolException(
                        ResponsesTurn.errorMessage(event),
                        event.toString());
                lane.open = false;
                lane.listener = null;
                lane.reducer = new ResponsesReducer();
                return ResponsesDispatch.failure(listener, decoded, failure);
            }
            if (kind == ResponsesTurn.Kind.CONTINUE) {
                return ResponsesDispatch.event(listener, decoded);
            }
            YoResponseObject snapshot = reducer.toObject();
            if (kind == ResponsesTurn.Kind.COMPLETE) {
                lane.open = false;
                lane.listener = null;
                lane.reducer = new ResponsesReducer();
            }
            return ResponsesDispatch.completed(listener, decoded, snapshot);
        }
    }

    public List<YoResponseStreamListener> failOpen() {
        List<YoResponseStreamListener> listeners = new ArrayList<>();
        for (Lane lane : lanes.values()) {
            synchronized (lane) {
                if (!lane.open || lane.listener == null) {
                    lane.open = false;
                    continue;
                }
                listeners.add(lane.listener);
                lane.open = false;
                lane.listener = null;
                lane.reducer = new ResponsesReducer();
            }
        }
        return listeners;
    }

    private static String display(String laneKey) {
        return laneKey.isEmpty() ? "(default)" : laneKey;
    }

    private static final class Lane {
        @Nullable
        private YoResponseStreamListener listener;
        private ResponsesReducer reducer = new ResponsesReducer();
        private boolean open;
    }

    public static final class ResponsesDispatch {
        public final YoResponseStreamListener listener;
        public final YoResponseStreamEvent event;
        @Nullable
        public final YoResponseObject completed;
        @Nullable
        public final YoAiException failure;

        private ResponsesDispatch(
                YoResponseStreamListener listener,
                YoResponseStreamEvent event,
                @Nullable YoResponseObject completed,
                @Nullable YoAiException failure) {
            this.listener = listener;
            this.event = event;
            this.completed = completed;
            this.failure = failure;
        }

        static ResponsesDispatch event(YoResponseStreamListener listener, YoResponseStreamEvent event) {
            return new ResponsesDispatch(listener, event, null, null);
        }

        static ResponsesDispatch completed(
                YoResponseStreamListener listener,
                YoResponseStreamEvent event,
                YoResponseObject completed) {
            return new ResponsesDispatch(listener, event, completed, null);
        }

        static ResponsesDispatch failure(
                YoResponseStreamListener listener,
                YoResponseStreamEvent event,
                YoAiException failure) {
            return new ResponsesDispatch(listener, event, null, failure);
        }
    }
}
