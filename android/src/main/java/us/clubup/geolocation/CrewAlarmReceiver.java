package us.clubup.geolocation;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


public class CrewAlarmReceiver extends BroadcastReceiver {

    // A tick runs only in a live service; a killed one is restarted by the system from its saved state.
    @Override
    public void onReceive(Context context, Intent intent) {

        GeolocationService.runCrewTickIfTracking();
    }
}
