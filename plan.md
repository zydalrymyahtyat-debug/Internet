1. **Per App Network Tracking**: To track real-time speed per app, we need to gather network statistics using `NetworkStatsManager` separated by UID, and resolve UID to Application Name and Icon. Since `TrafficStats` provides per-uid stats but not real-time rx/tx speed efficiently, we'll need to sample the bytes periodically.
2. **Connectivity Listener for Smart Visibility**: Register a `ConnectivityManager.NetworkCallback` in the service. If network is lost, hide the notification (stopForeground) or hide the icon. If network is regained, show it.
3. **Control Toggle**: In `MainActivity.kt`, add a button/switch to start/stop the `SpeedMonitorService` (Service toggle).
4. **Per App Usage List**: Build a UI component in `MainActivity.kt` that observes the per-app stats and displays them descendingly.
5. **Testing**: Run `./gradlew assembleDebug test`.
6. **Submit**: Ensure precommit checks and submit.
