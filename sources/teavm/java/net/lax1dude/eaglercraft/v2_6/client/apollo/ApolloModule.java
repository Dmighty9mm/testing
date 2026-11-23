package net.lax1dude.eaglercraft.v2_6.client.apollo;

import java.util.LinkedHashMap;
import java.util.Map;

import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoMessage;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoValues;

/**
 * One Apollo module (waypoint, title, ...): the messages it handles and
 * the per-connection state it keeps.
 *
 * <p>Lifecycle rules every module follows:</p>
 * <ul>
 * <li>{@link #reset()} drops all per-connection state; it runs on
 * disconnect / server switch and whenever the server disables the module.</li>
 * <li>While the server has the module disabled
 * (OverrideConfigurableSettingsMessage enable=false), its messages are
 * ignored.</li>
 * <li>{@link #properties} holds the module options the server sent with
 * the enable message ("tnt-ticks", "clear-title-on-server-switch", ...).</li>
 * </ul>
 */
public abstract class ApolloModule {

        /** Handles one decoded message. */
        public interface Handler {
                void handle(ProtoMessage msg);
        }

        private final String id;
        private final String displayName;
        private boolean enabled = true;
        private final Map<String, Object> properties = new LinkedHashMap<>();
        private int received;

        protected ApolloModule(String id, String displayName) {
                this.id = id;
                this.displayName = displayName;
        }

        /** Apollo module id as used in ConfigurableSettings.apollo_module ("waypoint"). */
        public final String id() {
                return id;
        }

        public final String displayName() {
                return displayName;
        }

        public final boolean isEnabled() {
                return enabled;
        }

        public final int receivedCount() {
                return received;
        }

        final void countReceived() {
                received++;
        }

        /** Registers this module's message handlers with the router. */
        protected abstract void register(Apollo.Router router);

        /** Drops every piece of per-connection state. */
        public abstract void reset();

        /** One-line state summary for the debug screen. */
        public String describeState() {
                return "";
        }

        /** The server changed this module's options (properties map). */
        protected void onPropertiesChanged() {
        }

        /** Called on the game thread every client tick (expiry, timers). */
        public void tick(long nowMs) {
        }

        final void setEnabled(boolean on) {
                if (enabled && !on) {
                        reset();
                }
                enabled = on;
        }

        final void putProperty(String key, Object value) {
                if (value == null) {
                        properties.remove(key);
                } else {
                        properties.put(key, value);
                }
        }

        final void clearForSession() {
                received = 0;
                enabled = true;
                properties.clear();
                reset();
        }

        public final Map<String, Object> properties() {
                return properties;
        }

        protected final boolean boolProperty(String key, boolean def) {
                return ProtoValues.asBool(properties.get(key), def);
        }

        protected final double numberProperty(String key, double def) {
                return ProtoValues.asNumber(properties.get(key), def);
        }
}
