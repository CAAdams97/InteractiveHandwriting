package group6.interactivehandwriting.common.network.nearby.connections.message;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by JakeL on 9/30/18.
 */

public enum NetworkMessageType {
    SERIAL(0),
    ROUTING_UPDATE_REQUEST(1),
    ROUTING_UPDATE_REPLY(2),

    RECEIVER_FPS(5),

    STREAM_STARTED(7),
    STREAM_ENDED(8),
    VIDEO_STREAM(9),

    START_DRAW(10),
    MOVE_DRAW(12),
    END_DRAW(13),
    UNDO_DRAW(14),

    SYNC_REQUEST(24),
    SYNC_REPLY(25);

    private int value;

    NetworkMessageType(int v) {
        value = v;
    }

    public int getValue() {
        return value;
    }

    private static Map<Integer, NetworkMessageType> lookup;

    static {
        lookup = new HashMap<>();
        for (NetworkMessageType type : NetworkMessageType.class.getEnumConstants()) {
            lookup.put(type.getValue(), type);
        }
    }

    public static NetworkMessageType get(Integer i) {
        return lookup.get(i);
    }
}
