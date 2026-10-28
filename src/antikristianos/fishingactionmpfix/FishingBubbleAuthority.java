package antikristianos.fishingactionmpfix;

import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.zed_0xff.zombie_buddy.Exposer;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.core.raknet.UdpConnection;
import zombie.iso.FishSchoolManager;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.network.GameClient;
import zombie.network.GameServer;

/**
 * Keeps multiplayer splash visibility tied to the Dedicated Server's authoritative anchor set.
 */
@Exposer.LuaClass(name = "FishingActionMPFix.FishingBubbleAuthority")
public final class FishingBubbleAuthority {
    static final long SERVER_REFRESH_NANOS = 1_000_000_000L;
    static final long RESET_REQUEST_INTERVAL_NANOS = 1_000_000_000L;
    static final int MIN_SCAN_RADIUS = 80;
    static final int MAX_SCAN_RADIUS = 128;
    private static final String COMMAND_MODULE = "FishingActionMPFix";
    private static final String SNAPSHOT_COMMAND = "fishingBubbleDelta";
    private static final String PAYLOAD_KEY = "payload";
    private static final Object SERVER_LOCK = new Object();
    private static final ThreadLocal<ClientSnapshot> ACTIVE_SPLASH_SNAPSHOT = new ThreadLocal<>();
    private static final Map<UdpConnection, ConnectionState> CONNECTION_STATES = new IdentityHashMap<>();

    private static volatile ClientSnapshot clientSnapshot = ClientSnapshot.empty();
    private static volatile Access access;
    private static volatile boolean initializationAttempted;
    private static long nextServerRefreshNanos;

    private FishingBubbleAuthority() {
    }

    public static void flushServerDeltas(FishSchoolManager manager) {
        if (!GameServer.server || manager == null || GameServer.udpEngine == null) {
            return;
        }

        long now = System.nanoTime();
        List<UdpConnection> connections = new ArrayList<>(GameServer.udpEngine.connections);
        synchronized (SERVER_LOCK) {
            if (now - nextServerRefreshNanos < 0L) {
                return;
            }
            nextServerRefreshNanos = now + SERVER_REFRESH_NANOS;
            CONNECTION_STATES.keySet().removeIf(connection -> !connections.contains(connection));
        }

        Access current = getAccess();
        if (current == null) {
            return;
        }

        for (UdpConnection connection : connections) {
            try {
                Map<Long, Anchor> anchors = buildAnchors(manager, connection, current);
                ConnectionState state;
                synchronized (SERVER_LOCK) {
                    state = CONNECTION_STATES.computeIfAbsent(connection, ignored -> new ConnectionState());
                }
                Delta delta = createDelta(state.anchors, anchors, !state.initialized);
                if (!delta.isEmpty() && sendDelta(connection, delta.encode())) {
                    state.recordDelivered(anchors);
                    FixLog.serverBubbleAuthorityActivated();
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
                FixLog.fishingSyncFailure("build or send a server-authoritative fishing-bubble delta", exception);
            }
        }
    }

    public static void requestImmediateServerRefresh() {
        synchronized (SERVER_LOCK) {
            nextServerRefreshNanos = 0L;
        }
    }

    public static boolean requestServerReset(IsoPlayer player) {
        if (!GameServer.server || player == null) {
            return false;
        }
        return requestServerReset(GameServer.getConnectionFromPlayer(player), System.nanoTime());
    }

    static boolean requestServerReset(UdpConnection connection, long now) {
        if (connection == null) {
            return false;
        }
        synchronized (SERVER_LOCK) {
            ConnectionState state = CONNECTION_STATES.computeIfAbsent(connection, ignored -> new ConnectionState());
            if (!state.requestReset(now)) {
                return false;
            }
            nextServerRefreshNanos = 0L;
        }
        FixLog.serverBubbleResetRequested();
        return true;
    }

    public static void applyClientDelta(String payload) {
        ClientSnapshot previous = clientSnapshot;
        clientSnapshot = applyDelta(previous, payload);
        FixLog.clientBubbleAuthorityActivated();
    }

    public static void clearClientAnchors() {
        clientSnapshot = ClientSnapshot.empty();
        ACTIVE_SPLASH_SNAPSHOT.remove();
    }

    public static void beginClientSplashPass() {
        beginClientSplashPass(GameClient.client);
    }

    public static void endClientSplashPass() {
        ACTIVE_SPLASH_SNAPSHOT.remove();
    }

    public static boolean authoritativeFishPointForSplash(int x, int y, boolean vanillaResult) {
        ClientSnapshot snapshot = ACTIVE_SPLASH_SNAPSHOT.get();
        if (snapshot == null) {
            return vanillaResult;
        }
        return snapshot.anchors.get(FishingStateSync.coordinatesToHash(x, y)) > 0;
    }

    public static int authoritativeFishCountForSplash(int x, int y, int vanillaResult) {
        ClientSnapshot snapshot = ACTIVE_SPLASH_SNAPSHOT.get();
        if (snapshot == null) {
            return vanillaResult;
        }
        return snapshot.anchors.get(FishingStateSync.coordinatesToHash(x, y)) > 0
                ? Math.max(1, vanillaResult)
                : 0;
    }

    public static boolean shouldSuppressSplashAtAnchor(int x, int y) {
        ClientSnapshot snapshot = ACTIVE_SPLASH_SNAPSHOT.get();
        return snapshot != null
                && !snapshot.anchors.containsKey(FishingStateSync.coordinatesToHash(x, y));
    }

    static void beginClientSplashPass(boolean multiplayerClient) {
        if (!multiplayerClient) {
            ACTIVE_SPLASH_SNAPSHOT.remove();
            return;
        }
        ACTIVE_SPLASH_SNAPSHOT.set(clientSnapshot);
    }

    static ClientSnapshot applyDelta(ClientSnapshot previous, String payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Fishing-bubble delta is missing");
        }
        String[] sections = payload.split("\\|", -1);
        if (sections.length != 3 || !("R".equals(sections[0]) || "D".equals(sections[0]))) {
            throw new IllegalArgumentException("Malformed fishing-bubble delta header");
        }

        TLongIntHashMap anchors = new TLongIntHashMap();
        if ("D".equals(sections[0]) && previous != null) {
            anchors.putAll(previous.anchors);
        }
        if (!sections[2].isBlank()) {
            String[] removals = sections[2].split(";");
            if (removals.length > 4096) {
                throw new IllegalArgumentException("Fishing-bubble removal delta exceeds the anchor limit");
            }
            for (String removal : removals) {
                String[] fields = removal.split(",", -1);
                if (fields.length != 2) {
                    throw new IllegalArgumentException("Malformed fishing-bubble removal: " + removal);
                }
                anchors.remove(FishingStateSync.coordinatesToHash(
                        Integer.parseInt(fields[0]),
                        Integer.parseInt(fields[1])));
            }
        }
        if (!sections[1].isBlank()) {
            String[] additions = sections[1].split(";");
            if (additions.length > 4096) {
                throw new IllegalArgumentException("Fishing-bubble addition delta exceeds the anchor limit");
            }
            for (String addition : additions) {
                String[] fields = addition.split(",", -1);
                if (fields.length != 3) {
                    throw new IllegalArgumentException("Malformed fishing-bubble addition: " + addition);
                }
                int kind = Integer.parseInt(fields[2]);
                if (kind != 0 && kind != 1) {
                    throw new IllegalArgumentException("Fishing-bubble anchor kind must be zero or one");
                }
                anchors.put(FishingStateSync.coordinatesToHash(
                        Integer.parseInt(fields[0]),
                        Integer.parseInt(fields[1])), kind);
            }
        }
        return new ClientSnapshot(anchors);
    }

    static float procedureRandomFloat(long x, long y, long seed) {
        x = x << 13 ^ x;
        y = y << 13 ^ y;
        seed = seed << 13 ^ seed;
        long value = x * 790169L
                + x * x * x * 15731L
                + y * 789221L
                + y * y * y * 16057L
                + seed * 788317L
                + seed * seed * seed * 15401L
                + x * y * 209123L
                + y * seed * 209581L
                + x * seed * 208501L
                + x * y * seed * 15749L
                + 1376312588L;
        return (float) (((value % 1073741824L) / 536870912.0D + 2.0D) / 4.0D);
    }

    static boolean proceduralFishPointCandidate(int x, int y, int seed) {
        return procedureRandomFloat(x, y, seed) > 0.995F;
    }

    static Delta createDelta(Map<Long, Anchor> previous, Map<Long, Anchor> current, boolean reset) {
        List<Anchor> additions = new ArrayList<>();
        List<Anchor> removals = new ArrayList<>();
        if (!reset) {
            for (Map.Entry<Long, Anchor> entry : previous.entrySet()) {
                Anchor currentAnchor = current.get(entry.getKey());
                if (currentAnchor == null || currentAnchor.kind() != entry.getValue().kind()) {
                    removals.add(entry.getValue());
                }
            }
        }
        for (Map.Entry<Long, Anchor> entry : current.entrySet()) {
            Anchor previousAnchor = previous.get(entry.getKey());
            if (reset || previousAnchor == null || previousAnchor.kind() != entry.getValue().kind()) {
                additions.add(entry.getValue());
            }
        }
        Comparator<Anchor> order = Comparator.comparingInt(Anchor::x).thenComparingInt(Anchor::y);
        additions.sort(order);
        removals.sort(order);
        return new Delta(reset, additions, removals);
    }

    private static Map<Long, Anchor> buildAnchors(
            FishSchoolManager manager,
            UdpConnection connection,
            Access current) throws ReflectiveOperationException {
        int seed = current.seed(manager);
        int scanRadius = Math.max(
                MIN_SCAN_RADIUS,
                Math.min(MAX_SCAN_RADIUS, Byte.toUnsignedInt(connection.getRelevantRange()) * 10 + 20));
        IsoCell cell = IsoCell.getInstance();
        TLongIntHashMap noisePoints = current.noiseFishPointDisabler();
        TLongObjectHashMap<?> chumPoints = current.chumPoints();
        Set<Long> visited = new HashSet<>();
        Map<Long, Anchor> anchors = new HashMap<>();

        if (connection.players == null) {
            return anchors;
        }
        for (IsoPlayer player : connection.players) {
            if (player == null) {
                continue;
            }
            int centerX = (int) Math.floor(player.getX());
            int centerY = (int) Math.floor(player.getY());
            for (int x = centerX - scanRadius; x <= centerX + scanRadius; x++) {
                for (int y = centerY - scanRadius; y <= centerY + scanRadius; y++) {
                    if (!connection.isRelevantTo(x, y)) {
                        continue;
                    }
                    long key = FishingStateSync.coordinatesToHash(x, y);
                    boolean fishPointCandidate = proceduralFishPointCandidate(x, y, seed);
                    boolean chumPoint = chumPoints.containsKey(key);
                    if ((!fishPointCandidate && !chumPoint) || !visited.add(key) || noisePoints.containsKey(key)) {
                        continue;
                    }
                    IsoGridSquare square = cell == null ? null : cell.getGridSquare(x, y, 0);
                    if (square == null || !square.getProperties().has(IsoFlagType.water)) {
                        continue;
                    }
                    int fishCount = fishPointCandidate && current.isFishPoint(manager, x, y)
                            ? current.getNumberOfFishInPoint(manager, x, y)
                            : 0;
                    if (fishCount > 0 || chumPoint) {
                        anchors.put(key, new Anchor(x, y, fishCount > 0 ? 1 : 0));
                    }
                }
            }
        }
        return anchors;
    }

    private static boolean sendDelta(UdpConnection connection, String payload) {
        if (LuaManager.platform == null) {
            return false;
        }
        KahluaTable arguments = LuaManager.platform.newTable();
        arguments.rawset(PAYLOAD_KEY, payload);
        GameServer.sendServerCommand(COMMAND_MODULE, SNAPSHOT_COMMAND, arguments, connection);
        return true;
    }

    private static Access getAccess() {
        if (initializationAttempted) {
            return access;
        }
        synchronized (FishingBubbleAuthority.class) {
            if (initializationAttempted) {
                return access;
            }
            try {
                access = Access.create();
            } catch (ReflectiveOperationException | RuntimeException exception) {
                FixLog.fishingSyncFailure("initialize server fishing-bubble reflection", exception);
                access = null;
            } finally {
                initializationAttempted = true;
            }
            return access;
        }
    }

    record Anchor(int x, int y, int kind) {
    }

    static final class Delta {
        private final boolean reset;
        private final List<Anchor> additions;
        private final List<Anchor> removals;

        Delta(boolean reset, List<Anchor> additions, List<Anchor> removals) {
            this.reset = reset;
            this.additions = additions;
            this.removals = removals;
        }

        boolean isEmpty() {
            return !reset && additions.isEmpty() && removals.isEmpty();
        }

        String encode() {
            StringBuilder payload = new StringBuilder((additions.size() + removals.size()) * 14 + 3);
            payload.append(reset ? 'R' : 'D').append('|');
            appendAnchors(payload, additions, true);
            payload.append('|');
            appendAnchors(payload, removals, false);
            return payload.toString();
        }

        private static void appendAnchors(StringBuilder payload, List<Anchor> anchors, boolean includeKind) {
            boolean first = true;
            for (Anchor anchor : anchors) {
                if (!first) {
                    payload.append(';');
                }
                first = false;
                payload.append(anchor.x()).append(',').append(anchor.y());
                if (includeKind) {
                    payload.append(',').append(anchor.kind());
                }
            }
        }
    }

    static record ClientSnapshot(TLongIntHashMap anchors) {
        static ClientSnapshot empty() {
            return new ClientSnapshot(new TLongIntHashMap());
        }
    }

    static final class ConnectionState {
        private Map<Long, Anchor> anchors = Map.of();
        private boolean initialized;
        private boolean resetRequestSeen;
        private long lastResetRequestNanos;

        boolean requestReset(long now) {
            if (resetRequestSeen && now - lastResetRequestNanos < RESET_REQUEST_INTERVAL_NANOS) {
                return false;
            }
            resetRequestSeen = true;
            lastResetRequestNanos = now;
            anchors = Map.of();
            initialized = false;
            return true;
        }

        boolean initialized() {
            return initialized;
        }

        int anchorCount() {
            return anchors.size();
        }

        void recordDelivered(Map<Long, Anchor> deliveredAnchors) {
            anchors = deliveredAnchors;
            initialized = true;
        }
    }

    private static final class Access {
        private final Field seedField;
        private final Field noiseFishPointDisablerField;
        private final Field chumPointsField;
        private final Method isFishPointMethod;
        private final Method getNumberOfFishInPointMethod;

        private Access(
                Field seedField,
                Field noiseFishPointDisablerField,
                Field chumPointsField,
                Method isFishPointMethod,
                Method getNumberOfFishInPointMethod) {
            this.seedField = seedField;
            this.noiseFishPointDisablerField = noiseFishPointDisablerField;
            this.chumPointsField = chumPointsField;
            this.isFishPointMethod = isFishPointMethod;
            this.getNumberOfFishInPointMethod = getNumberOfFishInPointMethod;
        }

        static Access create() throws ReflectiveOperationException {
            Field seedField = FishSchoolManager.class.getDeclaredField("seed");
            Field noiseFishPointDisablerField = FishSchoolManager.class.getDeclaredField("noiseFishPointDisabler");
            Field chumPointsField = FishSchoolManager.class.getDeclaredField("chumPoints");
            Method isFishPointMethod = FishSchoolManager.class.getDeclaredMethod("isFishPoint", int.class, int.class);
            Method getNumberOfFishInPointMethod =
                    FishSchoolManager.class.getDeclaredMethod("getNumberOfFishInPoint", int.class, int.class);
            seedField.setAccessible(true);
            noiseFishPointDisablerField.setAccessible(true);
            chumPointsField.setAccessible(true);
            isFishPointMethod.setAccessible(true);
            getNumberOfFishInPointMethod.setAccessible(true);
            return new Access(
                    seedField,
                    noiseFishPointDisablerField,
                    chumPointsField,
                    isFishPointMethod,
                    getNumberOfFishInPointMethod);
        }

        int seed(FishSchoolManager manager) throws IllegalAccessException {
            return seedField.getInt(manager);
        }

        TLongIntHashMap noiseFishPointDisabler() throws IllegalAccessException {
            Object value = noiseFishPointDisablerField.get(null);
            if (value instanceof TLongIntHashMap noisePoints) {
                return noisePoints;
            }
            throw new IllegalStateException("Unexpected noiseFishPointDisabler type");
        }

        TLongObjectHashMap<?> chumPoints() throws IllegalAccessException {
            Object value = chumPointsField.get(null);
            if (value instanceof TLongObjectHashMap<?> points) {
                return points;
            }
            throw new IllegalStateException("Unexpected chumPoints type");
        }

        boolean isFishPoint(FishSchoolManager manager, int x, int y) throws ReflectiveOperationException {
            return (boolean) isFishPointMethod.invoke(manager, x, y);
        }

        int getNumberOfFishInPoint(FishSchoolManager manager, int x, int y)
                throws ReflectiveOperationException {
            return (int) getNumberOfFishInPointMethod.invoke(manager, x, y);
        }
    }
}
