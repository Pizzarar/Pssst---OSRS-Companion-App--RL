package com.pssst.runelite;

/** Per-profile key/value storage for in-game observations (backed by ConfigManager's RS profile config in the plugin). */
public interface ObservationStore
{
	/** @return the active profile's key, or null when no profile is loaded yet (e.g. just after login) */
	String profileKey();

	String get(String key);

	void set(String key, String value);
}
