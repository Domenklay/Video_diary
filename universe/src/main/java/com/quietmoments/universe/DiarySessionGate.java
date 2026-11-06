package com.quietmoments.universe;

/**
 * In-memory unlock flag shared by the native diary and Universe activity.
 * An unlocked state is NEVER persisted to disk or restored after process death.
 */
public final class DiarySessionGate {
    private static volatile boolean authorized = false;
    private DiarySessionGate() {}
    public static boolean isAuthorized() { return authorized; }
    public static void unlock() { authorized = true; }
    public static void lock() { authorized = false; }
}
