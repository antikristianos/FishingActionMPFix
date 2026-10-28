package antikristianos.fishingactionmpfix;

import java.util.ArrayDeque;
import zombie.core.NetTimedAction;

/** Only the synchronous, accepted NetTimedAction replacement may retain fishing. */
public final class TimedActionFishingContext {
    private static final ThreadLocal<ArrayDeque<NetTimedAction>> PACKETS = new ThreadLocal<>();

    private TimedActionFishingContext() {
    }

    public static void push(NetTimedAction packet) {
        ArrayDeque<NetTimedAction> packets = PACKETS.get();
        if (packets == null) {
            packets = new ArrayDeque<>();
            PACKETS.set(packets);
        }
        packets.push(packet);
    }

    public static NetTimedAction current() {
        ArrayDeque<NetTimedAction> packets = PACKETS.get();
        return packets == null ? null : packets.peek();
    }

    public static void pop() {
        ArrayDeque<NetTimedAction> packets = PACKETS.get();
        if (packets == null) {
            return;
        }
        if (!packets.isEmpty()) {
            packets.pop();
        }
        if (packets.isEmpty()) {
            PACKETS.remove();
        }
    }
}
