package antikristianos.fishingactionmpfix;

import java.util.ArrayList;
import java.util.List;

final class ConnectionScopedActionSelector {
    interface Accessor<T> {
        byte actionId(T action) throws ReflectiveOperationException;

        Object connection(T action) throws ReflectiveOperationException;
    }

    private ConnectionScopedActionSelector() {
    }

    static <T> List<T> select(
            Iterable<T> actions,
            byte actionId,
            Object connection,
            Accessor<T> accessor) throws ReflectiveOperationException {
        List<T> matches = new ArrayList<>();
        for (T action : actions) {
            if (accessor.actionId(action) != actionId) {
                continue;
            }
            if (accessor.connection(action) == connection) {
                matches.add(action);
            }
        }
        return matches;
    }

    static <T> List<T> selectByActionId(
            Iterable<T> actions,
            byte actionId,
            Accessor<T> accessor) throws ReflectiveOperationException {
        List<T> matches = new ArrayList<>();
        for (T action : actions) {
            if (accessor.actionId(action) == actionId) {
                matches.add(action);
            }
        }
        return matches;
    }
}
