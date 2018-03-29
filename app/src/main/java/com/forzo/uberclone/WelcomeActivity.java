package com.forzo.uberclone;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.forzo.uberclone.common.Common;
import com.forzo.uberclone.remote.IGoogleAPI;
import com.github.glomadrian.materialanimatedswitch.MaterialAnimatedSwitch;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.SquareCap;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WelcomeActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {

    public static final int MY_PERMISSION_REQUEST_CODE = 7000;
    public static final int PLAY_SERVICE_REQUEST_CODE = 7000;
    public static final int UPDATE_INTERVAL = 5000;
    public static final int FASTEST_INTERVAL = 3000;
    public static final int DISPLACEMENT = 10;
    private static final String TAG = "WelcomeActivity";
    @BindView(R.id.location_switch)
    MaterialAnimatedSwitch locationSwitch;
    //    @BindView(R.id.edt_place)
    PlaceAutocompleteFragment edtPlace;
    //    @BindView(R.id.btn_go)
//    Button btnGo;
    private GoogleMap mMap;
    private LocationRequest locationRequest;
    private GoogleApiClient googleApiClient;
    private Location mLastLocation;
    private DatabaseReference drivers;
    private GeoFire geoFire;
    private Marker current;
    private SupportMapFragment mapFragment;

    //car animation
    private List<LatLng> polyLineList;
    private Marker pickupMarkerLocation;
    private float v;
    private double lat, lon;
    private Handler handler;
    private LatLng startPosition, endPosition, currentPosition;
    private int index, next;
    private String destination;
    private PolylineOptions polylineOptions, blackPolylineOptions;
    private Polyline blackPolyline, grayPolyline;
    //Retrofit network service
    private IGoogleAPI mService;
    private Marker carMarker;
    Runnable drawPathRunnable = new Runnable() {
        @Override
        public void run() {

            if (index < polyLineList.size() - 1) {
                index++;
                next = index + 1;
            }
            if (index <= polyLineList.size() - 1) {
                startPosition = polyLineList.get(index);
                endPosition = polyLineList.get(index);
            }

            ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1);
            valueAnimator.setDuration(3000);
            valueAnimator.setInterpolator(new LinearInterpolator());
            valueAnimator.addUpdateListener(animation -> {
                v = valueAnimator.getAnimatedFraction();
                lon = v * endPosition.longitude + (1 - v) * startPosition.longitude;
                lat = v * endPosition.latitude + (1 - v) * startPosition.latitude;

                LatLng newPos = new LatLng(lat, lon);
                carMarker.setPosition(newPos);
                carMarker.setAnchor(0.5f, 0.5f);
                carMarker.setRotation(getBearing(startPosition, newPos));

                mMap.moveCamera(CameraUpdateFactory.newCameraPosition(
                        new CameraPosition.Builder()
                                .target(newPos)
                                .zoom(15.5f)
                                .build()
                ));
            });

            valueAnimator.start();
            handler.postDelayed(this, 3000);
        }
    };
    private boolean isMarkerRotating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        ButterKnife.bind(this);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        locationSwitch.setOnCheckedChangeListener((boolean isOnline) -> {
            if (isOnline) {
                startLocationUpdates();
                displayLocation();
                Snackbar.make(mapFragment.getView(), R.string.you_are_online, Snackbar.LENGTH_LONG).show();
            } else {
                stopLocationUpdates();
                if (current != null)
                    current.remove();
                if (mMap != null)
                    mMap.clear();
                if (drawPathRunnable != null)
                    handler.removeCallbacks(drawPathRunnable);
                Snackbar.make(mapFragment.getView(), R.string.you_are_offline, Snackbar.LENGTH_LONG).show();
            }
        });

        //init car animation
        polyLineList = new ArrayList<>();

        edtPlace = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.edt_place);
        edtPlace.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                if (locationSwitch.isChecked()) {
                    destination = place.getAddress().toString();
                    destination = destination.replace(" ", ",");
                    mapFragment.getMapAsync(WelcomeActivity.this);
                    getDirection();
                } else
                    Toast.makeText(WelcomeActivity.this, "Please change Status to online ",
                            Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(Status status) {
                Toast.makeText(WelcomeActivity.this, " error " + status.toString(), Toast.LENGTH_LONG).show();
            }
        });
//
//        btnGo.setOnClickListener(view -> {
//            destination = edtPlace.getText().toString();
//            destination = destination.replace(" ", ",");
//            mapFragment.getMapAsync(WelcomeActivity.this);
//            getDirection();
//        });


        //save to geoFire and Firebase
        drivers = FirebaseDatabase.getInstance().getReference("Driver");
        geoFire = new GeoFire(drivers);

        setupLocation();

        mService = Common.getIGoogleAPI();

    }

    private float getBearing(LatLng startPosition, LatLng endPosition) {

        double lat = Math.abs(startPosition.latitude - endPosition.latitude);
        double lon = Math.abs(startPosition.longitude - endPosition.longitude);

        if (startPosition.latitude < endPosition.latitude && startPosition.longitude < endPosition.longitude) {
            return (float) (Math.toDegrees(Math.atan(lon / lat)));
        } else if (startPosition.latitude >= endPosition.latitude && startPosition.longitude < endPosition.longitude) {
            return (float) (90 - (Math.toDegrees(Math.atan(lon / lat))) + 90);
        } else if (startPosition.latitude < endPosition.latitude && startPosition.longitude >= endPosition.longitude) {
            return (float) (Math.toDegrees(Math.atan(lon / lat)) + 180);
        } else if (startPosition.latitude >= endPosition.latitude && startPosition.longitude >= endPosition.longitude) {
            return (float) (90 - (Math.toDegrees(Math.atan(lon / lat))) + 270);
        }
        return -1;
    }

    private void getDirection() {

        currentPosition = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());

        String requestApi;

        try {
            requestApi = Common.baseURL + "/maps/api/directions/json?" +
                    "origin=" + currentPosition.latitude + "," + currentPosition.longitude + "&" +
                    "destination=" + destination + "&" +
                    "key=" + getResources().getString(R.string.direction_api_key);

            Log.d(TAG, "getDirection: " + requestApi);
            mService.getPath(requestApi)
                    .enqueue(new Callback<String>() {
                        @Override
                        public void onResponse(Call<String> call, Response<String> response) {
                            try {
                                JSONObject jsonObject = new JSONObject(response.body().toString());
                                JSONArray jsonArray = jsonObject.getJSONArray("routes");
                                for (int i = 0; i < jsonArray.length(); i++) {
                                    JSONObject route = jsonArray.getJSONObject(i);
                                    JSONObject poly = route.getJSONObject("overview_polyline");
                                    String polyline = poly.getString("points");
                                    polyLineList = decodePoly(polyline);
                                }

                                //adjusting bounds
                                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                                for (LatLng latLng : polyLineList) {
                                    builder.include(latLng);
                                }

                                LatLngBounds bounds = builder.build();
                                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 2);
                                mMap.animateCamera(cameraUpdate);

                                polylineOptions = new PolylineOptions();
                                polylineOptions.color(Color.GRAY);
                                polylineOptions.width(5);
                                polylineOptions.startCap(new SquareCap());
                                polylineOptions.endCap(new SquareCap());
                                polylineOptions.jointType(JointType.ROUND);
                                polylineOptions.addAll(polyLineList);
                                grayPolyline = mMap.addPolyline(polylineOptions);

                                blackPolylineOptions = new PolylineOptions();
                                blackPolylineOptions.color(Color.BLACK);
                                blackPolylineOptions.width(5);
                                blackPolylineOptions.startCap(new SquareCap());
                                blackPolylineOptions.endCap(new SquareCap());
                                blackPolylineOptions.jointType(JointType.ROUND);
                                blackPolylineOptions.addAll(polyLineList);
                                blackPolyline = mMap.addPolyline(blackPolylineOptions);

                                mMap.addMarker(new MarkerOptions()
                                        .position(polyLineList.get(polyLineList.size() - 1))
                                        .title(getString(R.string.pick_up_location)));

                                //Animation
                                ValueAnimator polyLineValueAnimator = ValueAnimator.ofInt(0, 100);
                                polyLineValueAnimator.setDuration(2000);
                                polyLineValueAnimator.setInterpolator(new LinearInterpolator());
                                polyLineValueAnimator.addUpdateListener(animation -> {
                                    List<LatLng> points = grayPolyline.getPoints();
                                    int percentageValue = (int) polyLineValueAnimator.getAnimatedValue();
                                    int size = points.size();
                                    int newPoints = (int) (size * (percentageValue / 100.0f));
                                    List<LatLng> p = points.subList(0, newPoints);
                                    blackPolyline.setPoints(p);
                                });
                                polyLineValueAnimator.start();

                                carMarker = mMap.addMarker(new MarkerOptions()
                                        .position(currentPosition)
                                        .flat(true)
                                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.car)));

                                handler = new Handler();
                                index = -1;
                                next = 1;

                                handler.postDelayed(drawPathRunnable, 3000);

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onFailure(Call<String> call, Throwable t) {
                            Toast.makeText(WelcomeActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private List decodePoly(String encoded) {

        List poly = new ArrayList();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)),
                    (((double) lng / 1E5)));
            poly.add(p);
        }

        return poly;
    }

    private void setupLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (checkPlayServices()) {
                buildGoogleApiClient();
                createLocationRequest();
                if (locationSwitch.isChecked()) {
                    displayLocation();
                }
            }
        } else {
            // request permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void createLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(UPDATE_INTERVAL)
                .setFastestInterval(FASTEST_INTERVAL)
                .setSmallestDisplacement(DISPLACEMENT)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void buildGoogleApiClient() {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        googleApiClient.connect();
    }

    private boolean checkPlayServices() {
        int requestCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (requestCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(requestCode)) {
                GooglePlayServicesUtil.getErrorDialog(requestCode, this, PLAY_SERVICE_REQUEST_CODE).show();
            } else {
                Toast.makeText(this, R.string.this_device_is_not_supported, Toast.LENGTH_LONG).show();
                finish();
            }
        }
        return true;
    }

    private void startLocationUpdates() {

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this::onLocationChanged);

        } else {
            // request permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSION_REQUEST_CODE);
            }
        }

    }

    private void stopLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this::onLocationChanged);
        } else {
            // request permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void displayLocation() {

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);

            if (mLastLocation != null) {
                if (locationSwitch.isChecked()) {
                    final double latitude = mLastLocation.getLatitude();
                    final double longitude = mLastLocation.getLongitude();

                    geoFire.setLocation(FirebaseAuth.getInstance().getCurrentUser().getUid(),
                            new GeoLocation(latitude, longitude), (key, error) -> {
                                if (current != null) {
                                    current.remove();
                                }
                                current = mMap.addMarker(new MarkerOptions()
                                        .position(new LatLng(latitude, longitude))
                                        .title(getString(R.string.your_location)));

                                //animate camera
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, longitude),
                                        15.0f));
                                //draw animation rotate marker
                                rotateMarker(current, -360, mMap);

                            });
                }
            } else {
                Toast.makeText(this, "Cannot get your Location", Toast.LENGTH_LONG).show();
            }
        } else {
            // request permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSION_REQUEST_CODE);
            }
        }

    }

    private void rotateMarker(Marker current, float toRotation, GoogleMap mMap) {
        isMarkerRotating = false;
        if (!isMarkerRotating) {
            final Handler handler = new Handler();
            long start = SystemClock.uptimeMillis();
            final float startRotation = current.getRotation();
            final long duration = 1000;

            final Interpolator interpolator = new LinearInterpolator();

            handler.post(new Runnable() {
                @Override
                public void run() {
                    isMarkerRotating = true;
                    long elapsed = SystemClock.uptimeMillis() - start;
                    float t = interpolator.getInterpolation((float) elapsed / duration);
                    float r = t * toRotation + (1 - t) * startRotation;
                    current.setRotation(-r > 180 ? r / 2 : r);
                    if (t < 1.0) {
                        handler.postDelayed(this, 16);
                    } else {
                        isMarkerRotating = false;
                    }
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case MY_PERMISSION_REQUEST_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //permission was granted do nothing and carry on
                    if (checkPlayServices()) {
                        buildGoogleApiClient();
                        createLocationRequest();
                        if (locationSwitch.isChecked()) {
                            displayLocation();
                        }
                    }
                } else {
                    Toast.makeText(getApplicationContext(),
                            "This app requires mLastLocation permissions to be granted", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
        }
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mMap.setTrafficEnabled(false);
        mMap.setIndoorEnabled(false);
        mMap.setBuildingsEnabled(false);
        mMap.getUiSettings().setZoomControlsEnabled(true);

    }

    @Override
    public void onLocationChanged(Location location) {
        this.mLastLocation = location;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        startLocationUpdates();
        displayLocation();
    }

    @Override
    public void onConnectionSuspended(int i) {
        googleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
