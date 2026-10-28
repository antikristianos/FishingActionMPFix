package antikristianos.fishingactionmpfix;

import gnu.trove.iterator.TLongIntIterator;
import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import zombie.core.network.ByteBufferWriter;
import zombie.core.raknet.UdpConnection;
import zombie.iso.FishSchoolManager;
import zombie.iso.IsoMetaGrid;
import zombie.iso.IsoWorld;
import zombie.iso.zones.Zone;
import zombie.network.GameServer;
import zombie.network.PacketTypes;
import zombie.network.packets.INetworkPacket;
import zombie.network.packets.RegisterZonePacket;

public final class FishingStateSync {
    private static final String FISHING_ZONE_TYPE = "Fishing";
    private static final long SIGNATURE_UNAVAILABLE = Long.MIN_VALUE;
    private static final long FISHING_DATA_FLUSH_INTERVAL_NANOS = 1_000_000_000L;
    private static final Object FISHING_DATA_LOCK = new Object();

    private static volatile Access access;
    private static volatile boolean initializationAttempted;
    private static volatile PacketAccess packetAccess;
    private static volatile boolean packetInitializationAttempted;
    private static boolean fishingDataDirty;
    private static long nextFishingDataFlushNanos;

    private FishingStateSync() {
    }

    public static long noiseKeySignature() {
        Access current = getAccess();
        if (current == null) {
            return SIGNATURE_UNAVAILABLE;
        }

        try {
            TLongIntHashMap noisePoints = current.noiseFishPointDisabler();
            long signature = 0x9e3779b97f4a7c15L ^ noisePoints.size();
            TLongIntIterator iterator = noisePoints.iterator();
            while (iterator.hasNext()) {
                iterator.advance();
                signature += mix64(iterator.key());
            }
            return signature;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            FixLog.fishingSyncFailure("read the Vanilla fishing-noise state", exception);
            return SIGNATURE_UNAVAILABLE;
        }
    }

    public static void afterSoundNoise(long signatureBefore) {
        if (!GameServer.server || signatureBefore == SIGNATURE_UNAVAILABLE) {
            return;
        }

        long signatureAfter = noiseKeySignature();
        if (signatureAfter != SIGNATURE_UNAVAILABLE && signatureBefore != signatureAfter) {
            markFishingDataDirty();
            FishingBubbleAuthority.requestImmediateServerRefresh();
        }
    }

    public static void afterChumChange() {
        if (GameServer.server) {
            markFishingDataDirty();
        }
    }

    public static void afterFishCaught(int x, int y) {
        if (!GameServer.server) {
            return;
        }

        Zone fishingZone = findAuthoritativeFishingZoneAt(x, y);
        if (fishingZone == null) {
            FixLog.missingFishingZone(x, y);
            return;
        }

        GameServer.sendZone(fishingZone);
        FishingBubbleAuthority.requestImmediateServerRefresh();
        FixLog.fishingZoneSyncActivated();
    }

    public static void flushPendingFishingData(FishSchoolManager manager) {
        if (!GameServer.server || manager == null) {
            return;
        }

        long now = System.nanoTime();
        synchronized (FISHING_DATA_LOCK) {
            if (!fishingDataDirty || now < nextFishingDataFlushNanos) {
                return;
            }
            fishingDataDirty = false;
            nextFishingDataFlushNanos = now + FISHING_DATA_FLUSH_INTERVAL_NANOS;
        }

        broadcastFishingData(manager);
    }

    public static void invalidateFishingZoneCache() {
        Access current = getAccess();
        if (current == null) {
            return;
        }

        try {
            current.zoneCache().clear();
            FixLog.fishingCacheSyncActivated();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            FixLog.fishingSyncFailure("invalidate the Vanilla fishing-zone cache", exception);
        }
    }

    public static boolean processFishingZonePacket(Object packet) {
        PacketAccess current = getPacketAccess();
        if (current == null || packet == null) {
            return false;
        }

        try {
            FishingZonePacket data = current.read(packet);
            if (!FISHING_ZONE_TYPE.equals(data.type())) {
                return false;
            }

            IsoWorld world = IsoWorld.instance;
            IsoMetaGrid metaGrid = world == null ? null : world.getMetaGrid();
            if (metaGrid == null) {
                return false;
            }

            Zone fishingZone = applyExactFishingZone(metaGrid, data);
            seedFishingZoneCache(fishingZone);
            FixLog.exactFishingZonePacketSyncActivated();
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            FixLog.fishingSyncFailure("process an exact Vanilla fishing-zone packet", exception);
            return false;
        }
    }

    public static void afterSeedUpdated() {
        if (!GameServer.server || GameServer.udpEngine == null) {
            return;
        }

        List<UdpConnection> connections = new ArrayList<>(GameServer.udpEngine.connections);
        for (UdpConnection connection : connections) {
            sendFishingZones(connection);
        }
        FishingBubbleAuthority.requestImmediateServerRefresh();
        FixLog.dailyFishingZoneSyncActivated();
    }

    public static void sendFishingZones(UdpConnection connection) {
        if (!GameServer.server || connection == null) {
            return;
        }

        IsoWorld world = IsoWorld.instance;
        IsoMetaGrid metaGrid = world == null ? null : world.getMetaGrid();
        if (metaGrid == null) {
            return;
        }

        List<Zone> zones = fishingZonesInCachePrecedenceOrder(metaGrid.getZones());
        for (Zone zone : zones) {
            INetworkPacket.send(connection, PacketTypes.PacketType.RegisterZone, zone);
        }
    }

    static List<Zone> fishingZonesInCachePrecedenceOrder(Iterable<Zone> zones) {
        List<Zone> fishingZones = new ArrayList<>();
        if (zones != null) {
            for (Zone zone : zones) {
                if (isFishingZone(zone)) {
                    fishingZones.add(zone);
                }
            }
        }
        Collections.reverse(fishingZones);
        return fishingZones;
    }

    public static int seedFishingZoneCache(Zone fishingZone) {
        Access current = getAccess();
        if (current == null || !isFishingZone(fishingZone)) {
            return 0;
        }

        try {
            FishSchoolManager manager = FishSchoolManager.getInstance();
            int seeded = seedFishingZoneCache(
                    fishingZone,
                    (x, y) -> current.isFishPoint(manager, x, y));
            FixLog.fishingCachePrecedenceSyncActivated();
            return seeded;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            FixLog.fishingSyncFailure("rebuild the Vanilla fishing-zone cache precedence", exception);
            return 0;
        }
    }

    static int seedFishingZoneCache(Zone fishingZone, FishPointPredicate fishPointPredicate)
            throws ReflectiveOperationException {
        Access current = getAccess();
        if (current == null || !isFishingZone(fishingZone) || fishPointPredicate == null) {
            return 0;
        }

        int seeded = 0;
        int maxX = fishingZone.getX() + fishingZone.getWidth();
        int maxY = fishingZone.getY() + fishingZone.getHeight();
        for (int x = fishingZone.getX(); x <= maxX; x++) {
            for (int y = fishingZone.getY(); y <= maxY; y++) {
                if (fishPointPredicate.isFishPoint(x, y)) {
                    current.cacheFishingZone(coordinatesToHash(x, y), fishingZone);
                    seeded++;
                }
            }
        }
        return seeded;
    }

    @FunctionalInterface
    interface FishPointPredicate {
        boolean isFishPoint(int x, int y) throws ReflectiveOperationException;
    }

    static Zone findFishingZone(Iterable<Zone> zones) {
        if (zones == null) {
            return null;
        }
        for (Zone zone : zones) {
            if (isFishingZone(zone)) {
                return zone;
            }
        }
        return null;
    }

    static Zone findExactFishingZone(Iterable<Zone> zones, int x, int y, int z, int width, int height) {
        if (zones == null) {
            return null;
        }
        for (Zone zone : zones) {
            if (isExactFishingZone(zone, x, y, z, width, height)) {
                return zone;
            }
        }
        return null;
    }

    private static boolean isFishingZone(Zone zone) {
        return zone != null && FISHING_ZONE_TYPE.equals(zone.getType());
    }

    private static boolean isExactFishingZone(Zone zone, FishingZonePacket data) {
        return isExactFishingZone(zone, data.x(), data.y(), data.z(), data.width(), data.height());
    }

    private static boolean isExactFishingZone(Zone zone, int x, int y, int z, int width, int height) {
        return isFishingZone(zone)
                && zone.getX() == x
                && zone.getY() == y
                && zone.getZ() == z
                && zone.getWidth() == width
                && zone.getHeight() == height;
    }

    private static Zone applyExactFishingZone(IsoMetaGrid metaGrid, FishingZonePacket data) {
        List<Zone> zonesAtOrigin = new ArrayList<>(metaGrid.getZonesAt(data.x(), data.y(), data.z()));
        Zone packetZone = null;
        for (Zone zone : zonesAtOrigin) {
            if (isExactFishingZone(zone, data)) {
                updateFishingZone(zone, data);
                if (packetZone == null) {
                    packetZone = zone;
                }
            }
        }

        if (packetZone == null) {
            packetZone = metaGrid.registerZone(
                    data.name(),
                    data.type(),
                    data.x(),
                    data.y(),
                    data.z(),
                    data.width(),
                    data.height());
            if (packetZone == null) {
                throw new IllegalStateException("Vanilla failed to register an exact Fishing zone");
            }
            updateFishingZone(packetZone, data);
        }
        return packetZone;
    }

    private static void updateFishingZone(Zone zone, FishingZonePacket data) {
        zone.setName(data.name());
        zone.setOriginalName(data.name());
        zone.setLastActionTimestamp(data.lastActionTimestamp());
    }

    static Zone findAuthoritativeFishingZoneAt(int x, int y) {
        Access current = getAccess();
        if (current != null) {
            try {
                Zone cachedZone = current.cachedFishingZone(coordinatesToHash(x, y));
                if (isFishingZone(cachedZone)) {
                    return cachedZone;
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
                FixLog.fishingSyncFailure("read the server-selected fishing zone after a catch", exception);
            }
        }

        IsoWorld world = IsoWorld.instance;
        IsoMetaGrid metaGrid = world == null ? null : world.getMetaGrid();
        if (metaGrid == null) {
            return null;
        }
        return findFishingZone(metaGrid.getZonesAt(x, y, 0));
    }

    private static void markFishingDataDirty() {
        synchronized (FISHING_DATA_LOCK) {
            fishingDataDirty = true;
        }
    }

    private static void broadcastFishingData(FishSchoolManager manager) {
        if (GameServer.udpEngine == null) {
            return;
        }

        List<UdpConnection> connections = new ArrayList<>(GameServer.udpEngine.connections);
        for (UdpConnection connection : connections) {
            ByteBufferWriter writer = null;
            try {
                writer = connection.startPacket();
                PacketTypes.PacketType.FishingData.doPacket(writer);
                manager.setFishingData(writer);
                PacketTypes.PacketType.FishingData.send(connection);
            } catch (Throwable throwable) {
                if (writer != null) {
                    connection.cancelPacket();
                }
                FixLog.fishingSyncFailure("broadcast updated Vanilla fishing data", throwable);
            }
        }
        FixLog.fishingDataSyncActivated();
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }

    static long coordinatesToHash(int x, int y) {
        long transformedX = x >= 0 ? 2L * x : -2L * x - 1L;
        long transformedY = y >= 0 ? 2L * y : -2L * y - 1L;
        long paired = transformedX >= transformedY
                ? transformedX * transformedX + transformedX + transformedY
                : transformedX + transformedY * transformedY;
        paired /= 2L;
        return (x < 0) == (y < 0) ? paired : -paired - 1L;
    }

    private static Access getAccess() {
        if (initializationAttempted) {
            return access;
        }
        synchronized (FishingStateSync.class) {
            if (initializationAttempted) {
                return access;
            }
            try {
                access = Access.create();
            } catch (ReflectiveOperationException | RuntimeException exception) {
                FixLog.fishingSyncFailure("initialize Vanilla fishing-state reflection", exception);
                access = null;
            } finally {
                initializationAttempted = true;
            }
            return access;
        }
    }

    private static PacketAccess getPacketAccess() {
        if (packetInitializationAttempted) {
            return packetAccess;
        }
        synchronized (FishingStateSync.class) {
            if (packetInitializationAttempted) {
                return packetAccess;
            }
            try {
                packetAccess = PacketAccess.create();
            } catch (ReflectiveOperationException | RuntimeException exception) {
                FixLog.fishingSyncFailure("initialize Vanilla RegisterZone packet reflection", exception);
                packetAccess = null;
            } finally {
                packetInitializationAttempted = true;
            }
            return packetAccess;
        }
    }

    private record FishingZonePacket(
            String name,
            String type,
            int x,
            int y,
            int z,
            int width,
            int height,
            int lastActionTimestamp) {
    }

    private static final class PacketAccess {
        private final Field nameField;
        private final Field typeField;
        private final Field xField;
        private final Field yField;
        private final Field zField;
        private final Field widthField;
        private final Field heightField;
        private final Field lastActionTimestampField;

        private PacketAccess(
                Field nameField,
                Field typeField,
                Field xField,
                Field yField,
                Field zField,
                Field widthField,
                Field heightField,
                Field lastActionTimestampField) {
            this.nameField = nameField;
            this.typeField = typeField;
            this.xField = xField;
            this.yField = yField;
            this.zField = zField;
            this.widthField = widthField;
            this.heightField = heightField;
            this.lastActionTimestampField = lastActionTimestampField;
        }

        static PacketAccess create() throws ReflectiveOperationException {
            Field nameField = packetField("name");
            Field typeField = packetField("type");
            Field xField = packetField("x");
            Field yField = packetField("y");
            Field zField = packetField("z");
            Field widthField = packetField("w");
            Field heightField = packetField("h");
            Field lastActionTimestampField = packetField("lastActionTimestamp");
            return new PacketAccess(
                    nameField,
                    typeField,
                    xField,
                    yField,
                    zField,
                    widthField,
                    heightField,
                    lastActionTimestampField);
        }

        FishingZonePacket read(Object packet) throws IllegalAccessException {
            if (!RegisterZonePacket.class.isInstance(packet)) {
                throw new IllegalArgumentException("Unexpected RegisterZone packet type");
            }
            return new FishingZonePacket(
                    (String) nameField.get(packet),
                    (String) typeField.get(packet),
                    xField.getInt(packet),
                    yField.getInt(packet),
                    zField.getInt(packet),
                    widthField.getInt(packet),
                    heightField.getInt(packet),
                    lastActionTimestampField.getInt(packet));
        }

        private static Field packetField(String name) throws ReflectiveOperationException {
            Field field = RegisterZonePacket.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
    }

    private static final class Access {
        private final Field noiseFishPointDisablerField;
        private final Field zoneCacheField;
        private final Method isFishPointMethod;

        private Access(Field noiseFishPointDisablerField, Field zoneCacheField, Method isFishPointMethod) {
            this.noiseFishPointDisablerField = noiseFishPointDisablerField;
            this.zoneCacheField = zoneCacheField;
            this.isFishPointMethod = isFishPointMethod;
        }

        static Access create() throws ReflectiveOperationException {
            Field noiseFishPointDisablerField = FishSchoolManager.class.getDeclaredField("noiseFishPointDisabler");
            Field zoneCacheField = FishSchoolManager.class.getDeclaredField("zoneCache");
            Method isFishPointMethod = FishSchoolManager.class.getDeclaredMethod("isFishPoint", int.class, int.class);
            noiseFishPointDisablerField.setAccessible(true);
            zoneCacheField.setAccessible(true);
            isFishPointMethod.setAccessible(true);
            return new Access(noiseFishPointDisablerField, zoneCacheField, isFishPointMethod);
        }

        TLongIntHashMap noiseFishPointDisabler() throws IllegalAccessException {
            Object value = noiseFishPointDisablerField.get(null);
            if (value instanceof TLongIntHashMap noisePoints) {
                return noisePoints;
            }
            throw new IllegalStateException("Unexpected noiseFishPointDisabler type");
        }

        TLongObjectHashMap<?> zoneCache() throws IllegalAccessException {
            Object value = zoneCacheField.get(null);
            if (value instanceof TLongObjectHashMap<?> fishingZones) {
                return fishingZones;
            }
            throw new IllegalStateException("Unexpected zoneCache type");
        }

        boolean isFishPoint(FishSchoolManager manager, int x, int y) throws ReflectiveOperationException {
            return (boolean) isFishPointMethod.invoke(manager, x, y);
        }

        Zone cachedFishingZone(long key) throws IllegalAccessException {
            Object value = zoneCache().get(key);
            if (value instanceof FishSchoolManager.ZoneData zoneData) {
                return zoneData.zone;
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        void cacheFishingZone(long key, Zone zone) throws IllegalAccessException {
            TLongObjectHashMap<?> cache = zoneCache();
            ((TLongObjectHashMap<Object>) cache).put(key, new FishSchoolManager.ZoneData(zone));
        }
    }
}
