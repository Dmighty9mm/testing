package net.lax1dude.eaglercraft.v2_6.client.mods;

/** Key/value persistence (namespaced localStorage in the browser, a map in tests). */
public interface SettingsStore {

        String get(String key);

        void set(String key, String value);
}
