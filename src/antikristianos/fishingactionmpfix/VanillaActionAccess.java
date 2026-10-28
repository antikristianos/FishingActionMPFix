package antikristianos.fishingactionmpfix;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import zombie.characters.IsoPlayer;
import zombie.core.FishingAction;
import zombie.core.NetTimedAction;
import zombie.core.raknet.UdpConnection;
import zombie.network.GameServer;
import zombie.network.fields.character.ConnectionPlayer;
import zombie.network.server.AnimEventEmulator;

public final class VanillaActionAccess {
    public record PlayerResolution(boolean available, IsoPlayer player) {
        static PlayerResolution unavailable() {
            return new PlayerResolution(false, null);
        }
    }

    private static volatile Access access;
    private static volatile boolean initializationAttempted;

    private VanillaActionAccess() {
    }

    public static PlayerResolution resolveFishingPlayer(UdpConnection connection, byte actionId) {
        Access current = getAccess();
        if (current == null) {
            return PlayerResolution.unavailable();
        }

        try {
            List<Object> fishingActions = new ArrayList<>();
            for (Object action : current.actions()) {
                if (action instanceof FishingAction) {
                    fishingActions.add(action);
                }
            }

            List<Object> matches = ConnectionScopedActionSelector.select(
                    fishingActions,
                    actionId,
                    connection,
                    current);
            if (matches.size() == 1) {
                FixLog.activated();
                return new PlayerResolution(true, current.player(matches.get(0)));
            }

            FixLog.missingMatch(actionId);
            return new PlayerResolution(true, null);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            FixLog.reflectionFailure(exception);
            return PlayerResolution.unavailable();
        }
    }

    public static boolean removeForConnection(UdpConnection connection, byte actionId, boolean stopRequested) {
        Access current = getAccess();
        if (current == null) {
            return false;
        }

        try {
            List<Object> allActions = current.snapshot();
            List<Object> matches = ConnectionScopedActionSelector.select(
                    allActions,
                    actionId,
                    connection,
                    current);

            if (matches.isEmpty()) {
                if (current.hasActionId(allActions, actionId)) {
                    FixLog.missingMatch(actionId);
                    return true;
                }
                return false;
            }

            Collection<?> actions = current.actions();
            for (Object action : matches) {
                actions.remove(action);
            }
            if (stopRequested) {
                for (Object action : matches) {
                    current.stop(action);
                }
            }
            FixLog.activated();
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            FixLog.reflectionFailure(exception);
            return false;
        }
    }

    public static void reserveTimedActionId() {
        Access current = getAccess();
        if (current == null) {
            return;
        }
        try {
            List<Object> actions = current.snapshot();
            byte next = current.lastIdField.getByte(null);
            if (next == 0) {
                next = 1; // Vanilla's reserved action ID, unrelated to player online IDs.
            }
            boolean collidesWithFishing = false;
            for (Object action : actions) {
                if (action instanceof FishingAction && current.actionId(action) == next) {
                    collidesWithFishing = true;
                    break;
                }
            }
            if (!collidesWithFishing) {
                return;
            }
            for (int attempt = 0; attempt < 255; attempt++) {
                next++;
                if (next == 0) {
                    next = 1;
                }
                if (!current.hasActionId(actions, next)) {
                    current.lastIdField.setByte(null, next);
                    return;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            FixLog.reflectionFailure(exception);
        }
    }

    public static boolean stopForTimedAction(IsoPlayer playerId, NetTimedAction packet) {
        if (playerId == null || packet == null || packet.action == null
                || packet.type == null || packet.type.isEmpty()) {
            return false;
        }
        Access current = getAccess();
        if (current == null) {
            return false;
        }

        // Finish all reflective selection before touching the live queue. Vanilla has
        // already accepted this packet when it calls stopPlayerActions(playerId).
        List<Object> toStop = new ArrayList<>();
        Collection<Object> actions;
        boolean fishingRetained = false;
        try {
            ConnectionPlayer requestPlayer = current.playerId(packet);
            if (requestPlayer == null || requestPlayer.getPlayer() != playerId) {
                return false;
            }
            boolean keepFishing = !"ISPickupFishAction".equals(packet.type);
            actions = current.actions();
            for (Object action : current.snapshot()) {
                ConnectionPlayer owner = current.playerId(action);
                if (owner != null && owner.getPlayer() == playerId) {
                    if (keepFishing && action instanceof FishingAction) {
                        fishingRetained = true;
                    } else {
                        toStop.add(action);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            FixLog.reflectionFailure(exception);
            return false;
        }

        if (fishingRetained) {
            FixLog.concurrentFishingActivated();
        }
        // Remove exact objects, never a globally colliding byte-sized action ID.
        // Ordinary timed actions still replace each other, including emulator cleanup.
        for (Object action : toStop) {
            if (actions.remove(action)) {
                current.stop(action);
                if (action instanceof NetTimedAction timedAction) {
                    AnimEventEmulator.getInstance().remove(timedAction);
                }
            }
        }
        return true;
    }

    public static void repairClientFishingPacket(Object packet) {
        if (!(packet instanceof FishingAction)) {
            return;
        }

        Access current = getAccess();
        if (current == null) {
            return;
        }

        try {
            byte actionId = current.actionId(packet);
            List<Object> fishingActions = new ArrayList<>();
            for (Object action : current.actions()) {
                if (action instanceof FishingAction) {
                    fishingActions.add(action);
                }
            }

            List<Object> matches = ConnectionScopedActionSelector.selectByActionId(
                    fishingActions,
                    actionId,
                    current);
            if (matches.size() != 1) {
                return;
            }

            ConnectionPlayer packetPlayerId = current.playerId(packet);
            ConnectionPlayer localPlayerId = current.playerId(matches.get(0));
            if (packetPlayerId == null || localPlayerId == null) {
                return;
            }

            short receivedId = packetPlayerId.getId();
            short correctedId = localPlayerId.getId();
            packetPlayerId.copy(localPlayerId);
            if (receivedId != correctedId) {
                FixLog.clientStateRepair(actionId, receivedId, correctedId);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            FixLog.reflectionFailure(exception);
        }
    }

    private static Access getAccess() {
        if (initializationAttempted) {
            return access;
        }
        synchronized (VanillaActionAccess.class) {
            if (initializationAttempted) {
                return access;
            }
            try {
                access = Access.create();
            } catch (ReflectiveOperationException | RuntimeException exception) {
                FixLog.reflectionFailure(exception);
                access = null;
            } finally {
                initializationAttempted = true;
            }
            return access;
        }
    }

    private static final class Access implements ConnectionScopedActionSelector.Accessor<Object> {
        private final Field actionsField;
        private final Field actionIdField;
        private final Field playerIdField;
        private final Field lastIdField;
        private final Method stopMethod;

        private Access(Field actionsField, Field actionIdField, Field playerIdField, Field lastIdField, Method stopMethod) {
            this.actionsField = actionsField;
            this.actionIdField = actionIdField;
            this.playerIdField = playerIdField;
            this.lastIdField = lastIdField;
            this.stopMethod = stopMethod;
        }

        static Access create() throws ReflectiveOperationException {
            Class<?> actionClass = Class.forName("zombie.core.Action", false, VanillaActionAccess.class.getClassLoader());
            Class<?> managerClass = Class.forName("zombie.core.ActionManager", false, VanillaActionAccess.class.getClassLoader());

            Field actionsField = managerClass.getDeclaredField("actions");
            Field actionIdField = actionClass.getDeclaredField("id");
            Field playerIdField = actionClass.getDeclaredField("player");
            Field lastIdField = actionClass.getDeclaredField("lastId");
            Method stopMethod = actionClass.getDeclaredMethod("stop");

            actionsField.setAccessible(true);
            actionIdField.setAccessible(true);
            playerIdField.setAccessible(true);
            lastIdField.setAccessible(true);
            stopMethod.setAccessible(true);
            return new Access(actionsField, actionIdField, playerIdField, lastIdField, stopMethod);
        }

        @SuppressWarnings("unchecked")
        Collection<Object> actions() throws IllegalAccessException {
            return (Collection<Object>) actionsField.get(null);
        }

        List<Object> snapshot() throws IllegalAccessException {
            return new ArrayList<>(actions());
        }

        boolean hasActionId(Iterable<Object> actions, byte actionId) throws IllegalAccessException {
            for (Object action : actions) {
                if (actionId(action) == actionId) {
                    return true;
                }
            }
            return false;
        }

        IsoPlayer player(Object action) throws IllegalAccessException {
            ConnectionPlayer playerId = playerId(action);
            if (playerId == null) {
                return null;
            }
            return playerId.getPlayer();
        }

        ConnectionPlayer playerId(Object action) throws IllegalAccessException {
            Object value = playerIdField.get(action);
            return value instanceof ConnectionPlayer playerId ? playerId : null;
        }

        void stop(Object action) {
            try {
                stopMethod.invoke(action);
            } catch (IllegalAccessException | InvocationTargetException exception) {
                Throwable cause = exception instanceof InvocationTargetException invocation
                        && invocation.getCause() != null
                        ? invocation.getCause()
                        : exception;
                FixLog.stopFailure(cause);
            }
        }

        @Override
        public byte actionId(Object action) throws IllegalAccessException {
            return actionIdField.getByte(action);
        }

        @Override
        public Object connection(Object action) throws IllegalAccessException {
            IsoPlayer player = player(action);
            if (player == null) {
                return null;
            }
            return GameServer.getConnectionFromPlayer(player);
        }
    }
}
