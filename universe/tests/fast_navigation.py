"""Static regression contracts for warm Universe navigation.

CI cannot measure a physical device's frame timing. These checks protect the
activity reuse/handoff behavior that prevents repeated WebView startup.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[2]
java = (root / "universe/src/main/java/com/quietmoments/universe/UniverseActivity.java").read_text(encoding="utf-8")
main = (root / "app/src/main/java/com/quietmoments/diary/MainActivity.kt").read_text(encoding="utf-8")

assert "FLAG_ACTIVITY_REORDER_TO_FRONT" in java and "FLAG_ACTIVITY_REORDER_TO_FRONT" in main
assert "FLAG_ACTIVITY_SINGLE_TOP" in java and "FLAG_ACTIVITY_SINGLE_TOP" in main
assert "public static synchronized Bundle consumeNavigation()" in java
assert "UniverseActivity.consumeNavigation()" in main
assert "web.loadUrl(" in java and java.count("web.loadUrl(") == 1
assert "protected void onNewIntent(" in java and "setIntent(nextIntent)" in java
assert "web.onPause()" in java and "web.onResume()" in java
assert "if (switchingToNative) return;" in java
assert "onUniverseNavigationConsumed()" in main
assert "universeLauncher" not in main

navigation = java.split("private final class AppNavigationBridge", 1)[1].split("private final class StorageBridge", 1)[0]
assert "navigateToNative(" in navigation
assert "finish();" not in navigation
back = java.split("@Override public void onBackPressed()", 1)[1].split("@Override protected void onDestroy()", 1)[0]
assert "navigateToNative(" in back

print("Universe fast navigation contracts: OK")
