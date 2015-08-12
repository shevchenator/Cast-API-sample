package ru.shevchenko.cast;

import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import timber.log.Timber;

public class CastActivity extends AppCompatActivity {

    MediaRouter mediaRouter;
    MediaRouteSelector mediaRouteSelector;
    MyMediaRouterCallback mediaRouterCallback;
    CastDevice selectedCastDevice;
    GoogleApiClient castApiClient;
    RemoteMediaPlayer remoteMediaPlayer;
    Cast.Listener castClientListener = new Cast.Listener() {
        @Override
        public void onVolumeChanged() {
            if (castApiClient != null) {
                Timber.d("onVolumeChanged: " + Cast.CastApi.getVolume(castApiClient));
            }
        }

        @Override
        public void onApplicationDisconnected(int statusCode) {
            Timber.d("onApplicationDisconnected");
        }

        @Override
        public void onApplicationStatusChanged() {
            if (castApiClient != null) {
                Timber.d("onApplicationStatusChanged: " + Cast.CastApi.getApplicationStatus(castApiClient));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Timber.plant(new Timber.DebugTree());
        mediaRouter = MediaRouter.getInstance(getApplicationContext());
        mediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent
                        .categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                .build();
        mediaRouterCallback = new MyMediaRouterCallback();

        remoteMediaPlayer = new RemoteMediaPlayer();
        remoteMediaPlayer.setOnStatusUpdatedListener(new RemoteMediaPlayer.OnStatusUpdatedListener() {
            @Override
            public void onStatusUpdated() {
                MediaStatus mediaStatus = remoteMediaPlayer.getMediaStatus();
                Timber.d("onStatusUpdated: " + mediaStatus.getPlayerState());
            }
        });
        remoteMediaPlayer.setOnMetadataUpdatedListener(new RemoteMediaPlayer.OnMetadataUpdatedListener() {
            @Override
            public void onMetadataUpdated() {
                Timber.d("onMetadataUpdated: " + remoteMediaPlayer.getMediaInfo());
            }
        });
    }

    @Override
    protected void onStart() {
        super.onResume();
        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
    }

    @Override
    protected void onStop() {
        mediaRouter.removeCallback(mediaRouterCallback);
        if (castApiClient != null) {
            castApiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main, menu);
        MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        MediaRouteActionProvider mediaRouteActionProvider =
                (MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
        mediaRouteActionProvider.setRouteSelector(mediaRouteSelector);
        return true;
    }

    private class MyMediaRouterCallback extends MediaRouter.Callback {
        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo route) {
            selectedCastDevice = CastDevice.getFromBundle(route.getExtras());
            if (selectedCastDevice == null) {
                return;
            }

            Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions.builder(selectedCastDevice, castClientListener);

            castApiClient = new GoogleApiClient.Builder(CastActivity.this)
                    .addApi(Cast.API, apiOptionsBuilder.build())
                    .addConnectionCallbacks(new ConnectionCallbacks())
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(ConnectionResult connectionResult) {
                            Timber.d("onConnectionFailed: " + connectionResult);
                        }
                    })
                    .build();
            castApiClient.connect();
        }

        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo route) {
            if (castApiClient != null) {
                castApiClient.disconnect();
            }
            selectedCastDevice = null;
        }
    }

    private class ConnectionCallbacks implements GoogleApiClient.ConnectionCallbacks {

        @Override
        public void onConnected(Bundle bundle) {
            Cast.CastApi.launchApplication(castApiClient, CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID, false)
                    .setResultCallback(new ResultCallback<Cast.ApplicationConnectionResult>() {
                        @Override
                        public void onResult(Cast.ApplicationConnectionResult applicationConnectionResult) {
                            Status status = applicationConnectionResult.getStatus();
                            if (status.isSuccess()) {
                                MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_PHOTO);
                                MediaInfo mediaInfo = new MediaInfo.Builder("http://artsalon-uta.ru/wp-content/uploads/2015/03/muzteatr-omsk.jpg")
                                        .setContentType("image/jpeg")
                                        .setStreamType(MediaInfo.STREAM_TYPE_NONE)
                                        .setMetadata(mediaMetadata)
                                        .build();
                                remoteMediaPlayer.load(castApiClient, mediaInfo);
                            }
                        }
                    });
        }

        @Override
        public void onConnectionSuspended(int i) {
            Timber.d("onConnectionSuspended");
        }
    }
}
