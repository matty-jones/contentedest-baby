# Fix for Phone Not Syncing After Timezone Migration

## Problem

After running the timezone migration script, the phone's `last_server_clock` is higher than the events' `server_clock` values on the server. When the phone syncs, it asks for events with `server_clock > last_server_clock`, but all events have lower `server_clock` values, so the server returns nothing.

## Solution

Add a method to reset the sync clock to 0, which will force a full re-sync of all events.

## Code Changes Needed

### 1. Add method to EventRepository.kt

Add this method to `EventRepository` class (around line 229, after `updateServerClock`):

```kotlin
suspend fun resetSyncClock() = withContext(Dispatchers.IO) {
    syncStateDao.updateClock(0)
}
```

### 2. Update StatisticsScreen.kt

Add a new parameter and button. Update the function signature (around line 22):

```kotlin
@Composable
fun StatisticsScreen(
    vm: StatisticsViewModel,
    onNavigateBack: () -> Unit,
    onForceRepair: (() -> Unit)? = null,
    onForceSync: (() -> Unit)? = null,
    onResetSyncClock: (() -> Unit)? = null  // Add this
) {
```

Then add a button in the debug section (around line 123, after the Force Sync button):

```kotlin
if (onResetSyncClock != null) {
    Button(
        onClick = onResetSyncClock,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Reset Sync Clock")
    }
}
```

Also update the condition that shows the debug section (around line 81):

```kotlin
if (onForceRepair != null || onForceSync != null || onResetSyncClock != null) {
```

### 3. Update MainActivity.kt

Wire up the reset function (around line 297, in the StatisticsScreen call):

```kotlin
StatisticsScreen(
    vm = statsVm,
    onNavigateBack = { showStatisticsScreen = false },
    onForceRepair = null,
    onForceSync = {
        SyncWorker.triggerImmediateSync(this@MainActivity, deviceId)
    },
    onResetSyncClock = {
        lifecycleScope.launch {
            val eventRepository: EventRepository = hiltViewModel<StatisticsViewModel>().let { 
                // Get EventRepository from Hilt
                // Actually, better to inject it directly
            }
            // Better approach: inject EventRepository in MainActivity or pass it through
        }
    }
)
```

Actually, a better approach is to inject EventRepository in MainActivity. Add this to MainActivity:

```kotlin
@Inject
lateinit var eventRepository: EventRepository
```

Then in the StatisticsScreen call:

```kotlin
onResetSyncClock = {
    lifecycleScope.launch {
        eventRepository.resetSyncClock()
        SyncWorker.triggerImmediateSync(this@MainActivity, deviceId)
    }
}
```

## Alternative: Quick SQL Fix

If you have access to the phone's database (via ADB), you can reset it directly:

```bash
adb shell
run-as com.contentedest.baby
cd databases
sqlite3 app_database
UPDATE sync_state SET last_server_clock = 0 WHERE id = 1;
.exit
```

## Usage

After adding the code:
1. Open the app
2. Go to Statistics screen (menu → Statistics)
3. Click "Reset Sync Clock" button
4. Click "Force Sync" button
5. All events should now sync with corrected timestamps

