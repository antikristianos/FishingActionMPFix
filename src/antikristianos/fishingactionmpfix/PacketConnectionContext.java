package antikristianos.fishingactionmpfix;

import java.util.ArrayDeque;
import zombie.core.raknet.UdpConnection;

public final class PacketConnectionContext {
    public enum Kind {
        FISHING,
        GENERAL
    }

    private record Frame(Kind kind, UdpConnection connection) {
    }

    private static final ThreadLocal<ArrayDeque<Frame>> FRAMES = new ThreadLocal<>();

    private PacketConnectionContext() {
    }

    public static void push(Kind kind, UdpConnection connection) {
        ArrayDeque<Frame> frames = FRAMES.get();
        if (frames == null) {
            frames = new ArrayDeque<>();
            FRAMES.set(frames);
        }
        frames.push(new Frame(kind, connection));
    }

    public static void pop() {
        ArrayDeque<Frame> frames = FRAMES.get();
        if (frames == null) {
            return;
        }
        if (!frames.isEmpty()) {
            frames.pop();
        }
        if (frames.isEmpty()) {
            FRAMES.remove();
        }
    }

    public static UdpConnection currentConnection() {
        ArrayDeque<Frame> frames = FRAMES.get();
        Frame frame = frames == null ? null : frames.peek();
        return frame == null ? null : frame.connection();
    }

    public static UdpConnection currentFishingConnection() {
        ArrayDeque<Frame> frames = FRAMES.get();
        Frame frame = frames == null ? null : frames.peek();
        if (frame == null || frame.kind() != Kind.FISHING) {
            return null;
        }
        return frame.connection();
    }
}
