package irinque.zenixac.platform;

import irinque.zenixac.checks.PacketOrderCheck;
import irinque.zenixac.checks.PacketOrderCheck.BlockPosition;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class PacketEventsBridge implements AutoCloseable {
    private static final String MINIMUM_VERSION = "2.14.0";
    private static final String PACKET_EVENTS_PLUGIN = "packetevents";

    private final Plugin plugin;
    private final PacketOrderCheck packetOrderCheck;
    private final AtomicBoolean packetFailureLogged = new AtomicBoolean();
    private Object eventManager;
    private Object registeredListener;
    private Method packetTypeMethod;
    private Method cancelledMethod;
    private Method userMethod;
    private Method playerMethod;
    private Method userUuidMethod;
    private Method actionMethod;
    private Method blockPositionMethod;
    private Method blockXMethod;
    private Method blockYMethod;
    private Method blockZMethod;

    public PacketEventsBridge(Plugin plugin, PacketOrderCheck packetOrderCheck) {
        this.plugin = plugin;
        this.packetOrderCheck = packetOrderCheck;
    }

    public boolean refresh(boolean enabled) {
        if (!enabled) {
            close();
            return true;
        }
        return register();
    }

    public boolean register() {
        if (registeredListener != null) {
            return true;
        }
        Plugin packetEvents = plugin.getServer().getPluginManager().getPlugin(PACKET_EVENTS_PLUGIN);
        if (packetEvents == null || !packetEvents.isEnabled()) {
            return false;
        }
        String version = packetEvents.getDescription().getVersion();
        if (compareVersions(version, MINIMUM_VERSION) < 0) {
            plugin.getLogger().warning("[ZenixAC] Packet-order check needs PacketEvents "
                    + MINIMUM_VERSION + " or newer; found " + version);
            return false;
        }

        try {
            ClassLoader classLoader = packetEvents.getClass().getClassLoader();
            Class<?> packetEventsClass = Class.forName("com.github.retrooper.packetevents.PacketEvents", true, classLoader);
            Object api = packetEventsClass.getMethod("getAPI").invoke(null);
            eventManager = api.getClass().getMethod("getEventManager").invoke(api);

            Class<?> listenerType = Class.forName(
                    "com.github.retrooper.packetevents.event.PacketListener", true, classLoader
            );
            Class<?> packetEventType = Class.forName(
                    "com.github.retrooper.packetevents.event.PacketReceiveEvent", true, classLoader
            );
            Class<?> protocolPacketEventType = Class.forName(
                    "com.github.retrooper.packetevents.event.ProtocolPacketEvent", true, classLoader
            );
            packetTypeMethod = protocolPacketEventType.getMethod("getPacketType");
            cancelledMethod = protocolPacketEventType.getMethod("isCancelled");
            userMethod = protocolPacketEventType.getMethod("getUser");
            playerMethod = protocolPacketEventType.getMethod("getPlayer");
            userUuidMethod = Class.forName(
                    "com.github.retrooper.packetevents.protocol.player.User", true, classLoader
            ).getMethod("getUUID");
            Class<?> diggingWrapperType = Class.forName(
                    "com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging",
                    true, classLoader
            );
            Constructor<?> diggingWrapper = diggingWrapperType.getConstructor(packetEventType);
            actionMethod = diggingWrapperType.getMethod("getAction");
            blockPositionMethod = diggingWrapperType.getMethod("getBlockPosition");
            Class<?> blockPositionType = Class.forName(
                    "com.github.retrooper.packetevents.util.Vector3i", true, classLoader
            );
            blockXMethod = blockPositionType.getMethod("getX");
            blockYMethod = blockPositionType.getMethod("getY");
            blockZMethod = blockPositionType.getMethod("getZ");
            Object packetType = diggingPacketType(classLoader);
            InvocationHandler handler = (proxy, method, arguments) -> {
                if (method.getName().equals("onPacketReceive") && arguments != null && arguments.length == 1) {
                    handlePacket(arguments[0], packetType, diggingWrapper);
                    return null;
                }
                if (method.isDefault()) {
                    return InvocationHandler.invokeDefault(proxy, method, arguments);
                }
                return objectMethod(proxy, method, arguments);
            };
            Object listener = Proxy.newProxyInstance(
                    listenerType.getClassLoader(), new Class<?>[]{listenerType}, handler
            );
            Class<?> priorityType = Class.forName(
                    "com.github.retrooper.packetevents.event.PacketListenerPriority", true, classLoader
            );
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object normalPriority = Enum.valueOf((Class<? extends Enum>) priorityType.asSubclass(Enum.class), "MONITOR");
            Method registerMethod = eventManager.getClass().getMethod("registerListener", listenerType, priorityType);
            registeredListener = registerMethod.invoke(eventManager, listener, normalPriority);
            plugin.getLogger().info("[ZenixAC] PacketEvents listener registered (version " + version + ")");
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "[ZenixAC] PacketEvents was found but packet-order monitoring could not be registered", exception);
            eventManager = null;
            registeredListener = null;
            return false;
        }
    }

    @Override
    public void close() {
        if (eventManager == null || registeredListener == null) {
            return;
        }
        try {
            for (Method method : eventManager.getClass().getMethods()) {
                if (method.getName().equals("unregisterListener") && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isInstance(registeredListener)) {
                    method.invoke(eventManager, registeredListener);
                    break;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "[ZenixAC] Could not unregister PacketEvents listener", exception);
        } finally {
            eventManager = null;
            registeredListener = null;
        }
    }

    private void handlePacket(Object event, Object diggingPacketType, Constructor<?> wrapperConstructor) {
        try {
            Object packetType = packetTypeMethod.invoke(event);
            if (!diggingPacketType.equals(packetType) || Boolean.TRUE.equals(cancelledMethod.invoke(event))) {
                return;
            }
            Object user = userMethod.invoke(event);
            Object uuid = userUuidMethod.invoke(user);
            Object playerObject = playerMethod.invoke(event);
            if (!(uuid instanceof UUID playerId) || !(playerObject instanceof Player player)) {
                return;
            }

            Object wrapper = wrapperConstructor.newInstance(event);
            Object action = actionMethod.invoke(wrapper);
            Object position = blockPositionMethod.invoke(wrapper);
            BlockPosition blockPosition = new BlockPosition(
                    ((Number) blockXMethod.invoke(position)).intValue(),
                    ((Number) blockYMethod.invoke(position)).intValue(),
                    ((Number) blockZMethod.invoke(position)).intValue()
            );
            packetOrderCheck.onDiggingPacket(player, playerId, action.toString(), blockPosition);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (packetFailureLogged.compareAndSet(false, true)) {
                plugin.getLogger().log(Level.WARNING,
                        "[ZenixAC] Could not read a PacketEvents digging packet; packet-order monitoring is skipped",
                        exception);
            }
        }
    }

    private Object diggingPacketType(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> clientPacketTypes = Class.forName(
                "com.github.retrooper.packetevents.protocol.packettype.PacketType$Play$Client", true, classLoader
        );
        Field field = clientPacketTypes.getField("PLAYER_DIGGING");
        return field.get(null);
    }

    private Object objectMethod(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "toString" -> "ZenixAC PacketEvents listener";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (arguments == null || arguments.length == 0 ? null : arguments[0]);
            default -> null;
        };
    }

    private static int compareVersions(String left, String right) {
        int[] leftParts = versionParts(left);
        int[] rightParts = versionParts(right);
        for (int index = 0; index < Math.max(leftParts.length, rightParts.length); index++) {
            int leftPart = index < leftParts.length ? leftParts[index] : 0;
            int rightPart = index < rightParts.length ? rightParts[index] : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    private static int[] versionParts(String version) {
        String numericVersion = version.split("[-+ ]", 2)[0];
        String[] parts = numericVersion.split("\\.");
        int[] result = new int[parts.length];
        for (int index = 0; index < parts.length; index++) {
            String digits = parts[index].replaceAll("[^0-9].*$", "");
            result[index] = digits.isBlank() ? 0 : Integer.parseInt(digits);
        }
        return result;
    }
}
