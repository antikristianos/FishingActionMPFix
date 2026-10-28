package antikristianos.fishingactionmpfix;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class FixLog {
    private static final AtomicBoolean ACTIVATION_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean CONCURRENT_FISHING_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean CLIENT_STATE_REPAIR_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean REFLECTION_FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FISHING_ZONE_SYNC_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FISHING_DATA_SYNC_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FISHING_CACHE_SYNC_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FISHING_CACHE_PRECEDENCE_SYNC_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean EXACT_FISHING_ZONE_PACKET_SYNC_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean DAILY_FISHING_ZONE_SYNC_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean SERVER_BUBBLE_AUTHORITY_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean CLIENT_BUBBLE_AUTHORITY_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean SERVER_BUBBLE_RESET_REQUEST_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FISHING_SYNC_FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicInteger MISSING_MATCHES = new AtomicInteger();
    private static final AtomicInteger MISSING_FISHING_ZONES = new AtomicInteger();

    private FixLog() {
    }

    static void activated() {
        if (ACTIVATION_LOGGED.compareAndSet(false, true)) {
            System.out.println("[FishingActionMPFix] Connection-scoped action routing is active.");
        }
    }

    static void concurrentFishingActivated() {
        if (CONCURRENT_FISHING_LOGGED.compareAndSet(false, true)) {
            System.out.println("[FishingActionMPFix] Server fishing continues during inventory timed actions.");
        }
    }

    static void reflectionFailure(Throwable throwable) {
        if (REFLECTION_FAILURE_LOGGED.compareAndSet(false, true)) {
            System.err.println("[FishingActionMPFix] Vanilla action reflection is unavailable; leaving Vanilla behavior unchanged.");
            throwable.printStackTrace(System.err);
        }
    }

    static void clientStateRepair(byte actionId, short packetPlayerId, short localPlayerId) {
        if (CLIENT_STATE_REPAIR_LOGGED.compareAndSet(false, true)) {
            System.out.println("[FishingActionMPFix] Repaired client FishingAction state correlation for action id "
                    + Byte.toUnsignedInt(actionId) + ": packet player id " + packetPlayerId
                    + " -> local player id " + localPlayerId + ".");
        }
    }

    static void missingMatch(byte actionId) {
        int count = MISSING_MATCHES.incrementAndGet();
        if (count <= 5 || count % 100 == 0) {
            System.err.println("[FishingActionMPFix] No action owned by the sending connection for action id "
                    + Byte.toUnsignedInt(actionId) + "; foreign actions were not used. occurrence=" + count);
        }
    }

    static void stopFailure(Throwable throwable) {
        System.err.println("[FishingActionMPFix] Failed to stop a connection-scoped action after removing it.");
        throwable.printStackTrace(System.err);
    }

    static void fishingZoneSyncActivated() {
        if (FISHING_ZONE_SYNC_LOGGED.compareAndSet(false, true)) {
            System.out.println("[FishingActionMPFix] Server-authoritative fishing-zone depletion sync is active.");
        }
    }

    static void fishingDataSyncActivated() {
        if (FISHING_DATA_SYNC_LOGGED.compareAndSet(false, true)) {
            System.out.println("[FishingActionMPFix] Server-authoritative fishing noise/chum sync is active.");
        }
    }

    static void fishingCacheSyncActivated() {
        if (FISHING_CACHE_SYNC_LOGGED.compareAndSet(false, true)) {
            System.out.println("[FishingActionMPFix] Client fishing-zone cache invalidation is active.");
        }
    }

    static void fishingCachePrecedenceSyncActivated() {
        if (FISHING_CACHE_PRECEDENCE_SYNC_LOGGED.compareAndSet(false, true)) {
            System.out.println("[FishingActionMPFix] Server-ordered client fishing-zone cache reconstruction is active.");
        }
    }

    static void exactFishingZonePacketSyncActivated() {
        if (EXACT_FISHING_ZONE_PACKET_SYNC_LOGGED.compareAndSet(false, true)) {
            System.out.println("[FishingActionMPFix] Exact client fishing-zone packet matching is active.");
        }
    }

    static void dailyFishingZoneSyncActivated() {
        if (DAILY_FISHING_ZONE_SYNC_LOGGED.compareAndSet(false, true)) {
            System.out.println("[FishingActionMPFix] Daily fishing-zone resynchronization is active.");
        }
    }

    static void serverBubbleAuthorityActivated() {
        if (SERVER_BUBBLE_AUTHORITY_LOGGED.compareAndSet(false, true)) {
            System.out.println("[FishingActionMPFix] Server-authoritative fishing-bubble deltas are active.");
        }
    }

    static void clientBubbleAuthorityActivated() {
        if (CLIENT_BUBBLE_AUTHORITY_LOGGED.compareAndSet(false, true)) {
            System.out.println("[FishingActionMPFix] Client fishing bubbles are filtered by server-authoritative fish points.");
        }
    }

    static void serverBubbleResetRequested() {
        if (SERVER_BUBBLE_RESET_REQUEST_LOGGED.compareAndSet(false, true)) {
            System.out.println("[FishingActionMPFix] Client-ready fishing-bubble reset handshake is active.");
        }
    }

    static void fishingSyncFailure(String operation, Throwable throwable) {
        if (FISHING_SYNC_FAILURE_LOGGED.compareAndSet(false, true)) {
            System.err.println("[FishingActionMPFix] Failed to " + operation
                    + "; see the stack trace for the affected synchronization path.");
            throwable.printStackTrace(System.err);
        }
    }

    static void missingFishingZone(int x, int y) {
        int count = MISSING_FISHING_ZONES.incrementAndGet();
        if (count <= 5 || count % 100 == 0) {
            System.err.println("[FishingActionMPFix] No Fishing zone found after catch at "
                    + x + "," + y + "; depletion update was not broadcast. occurrence=" + count);
        }
    }
}
