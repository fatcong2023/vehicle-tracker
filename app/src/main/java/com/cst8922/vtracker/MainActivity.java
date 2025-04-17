package com.cst8922.vtracker;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

// MQTT imports
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;

import com.cst8922.vtracker.databinding.ActivityMainBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.google.android.gms.maps.model.BitmapDescriptor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLSocketFactory;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    // UI elements for location info panel
    private TextView locationTextView;
    private TextView coordinatesTextView;
    private TextView statusTextView;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    // Add these instance variables with your other UI elements
    private View statusIndicatorBox;
    private TextView statusIndicatorText;

    // Variables for route simulation
    private List<LatLng> routeCoordinates = new ArrayList<>();
    private int currentRouteIndex = 0;
    private Handler routeHandler = new Handler(Looper.getMainLooper());
    private static final int ROUTE_UPDATE_INTERVAL = 500; // Update every 0.5 seconds

    private MqttClient mqttClient;
    private String mqttBroker = "ssl://indigosweat-0hmo2q.a02.usw2.aws.hivemq.cloud:8883";
    private String mqttTopic = "gps/devices/+/location"; // Subscribe to all cars
    private boolean mqttConnected = false;

    private boolean isAutoTracking = true;
    private FloatingActionButton centerButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        try {
            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
            appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        } catch (Exception e) {
            // Navigation might not be set up correctly
            e.printStackTrace();
        }

        // Initialize the info panel TextViews
        try {
            // Existing code for TextViews
            locationTextView = findViewById(R.id.location_text);
            coordinatesTextView = findViewById(R.id.coordinates_text);
            statusTextView = findViewById(R.id.status_text);

            // Add new status indicator elements
            statusIndicatorBox = findViewById(R.id.status_indicator_box);
            statusIndicatorText = findViewById(R.id.status_indicator_text);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Initialize the fusedLocationClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Load route coordinates from route.txt
//        loadRouteCoordinates();

        // Get the SupportMapFragment and request notification when the map is ready
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (statusTextView != null) {
                    statusTextView.setText("Status: Connecting to Vehicle location...");
                }
                if (!mqttConnected) {

                    Snackbar.make(view, "Connecting to live vehicle data...", Snackbar.LENGTH_LONG).setAnchorView(R.id.fab).setAction("Action", null).show();
                    connectToMQTT();
//                    Snackbar.make(view, "Connecting to live vehicle data...", Snackbar.LENGTH_LONG).setAnchorView(R.id.fab).setAction("Action", null).show();
                } else {
                    Snackbar.make(view, "Already connected to live data", Snackbar.LENGTH_LONG).setAnchorView(R.id.fab).setAction("Action", null).show();
                }
            }
        });

        centerButton = findViewById(R.id.center_button);
        centerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isAutoTracking = !isAutoTracking;

                // Change button appearance based on tracking state
                if (isAutoTracking) {
                    centerButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(MainActivity.this, android.R.color.holo_green_light)));
                    Snackbar.make(view, "Auto-tracking enabled", Snackbar.LENGTH_SHORT).setAnchorView(R.id.center_button).show();

                    // If we have a current location, center on it
                    if (mMap != null && !routeCoordinates.isEmpty() && currentRouteIndex > 0 && currentRouteIndex <= routeCoordinates.size()) {
                        LatLng currentLocation = routeCoordinates.get(currentRouteIndex - 1);
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));
                    }
                } else {
                    centerButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(MainActivity.this, android.R.color.white)));
                    Snackbar.make(view, "Auto-tracking disabled", Snackbar.LENGTH_SHORT).setAnchorView(R.id.center_button).show();
                }
            }
        });
    }

    // Method to load route coordinates from route.txt
    private void loadRouteCoordinates() {
        try {
            // Add debug output
            if (statusTextView != null) {
                statusTextView.setText("Status: Loading route coordinates...");
            }

            InputStream is = getResources().openRawResource(R.raw.route);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;

            while ((line = reader.readLine()) != null) {
                // Log each line for debugging
                System.out.println("Route line: " + line);

                line = line.trim().replace("[", "").replace("]", "");
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    try {
                        double lat = Double.parseDouble(parts[0].trim());
                        double lng = Double.parseDouble(parts[1].trim());
                        routeCoordinates.add(new LatLng(lat, lng));
                        System.out.println("Added point: " + lat + ", " + lng);
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }

            reader.close();
            is.close();

//            if (statusTextView != null) {
//                statusTextView.setText("Status: Loaded " + routeCoordinates.size() + " route points");
//            }

        } catch (IOException e) {
            e.printStackTrace();
            if (statusTextView != null) {
                statusTextView.setText("Status: Error loading route - " + e.getMessage());
            }
        }
    }

    // Method to start route simulation
    private void startRouteSimulation() {
        if (routeCoordinates.isEmpty()) {
            if (statusTextView != null) {
                statusTextView.setText("Status: No route coordinates available");
            }
            return;
        }

        // Reset to beginning if we've reached the end
        if (currentRouteIndex >= routeCoordinates.size()) {
            currentRouteIndex = 0;
        }

        // Remove any pending route updates
        routeHandler.removeCallbacksAndMessages(null);

        // Start the route simulation
        simulateNextRoutePoint();
    }

    private void connectToMQTT() {
        try {
            // Create a unique client ID
            String clientId = "AndroidClient-" + System.currentTimeMillis();
            mqttClient = new MqttClient(mqttBroker, clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(30);
            
            // Set authentication credentials
            options.setUserName("*****");
            options.setPassword("*****".toCharArray());
            
            // Configure SSL/TLS
            options.setSocketFactory(SSLSocketFactory.getDefault());

            // Set callback for MQTT events
            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    mqttConnected = false;
                    runOnUiThread(() -> {
                        if (statusTextView != null) {
                            statusTextView.setText("Status: MQTT Connection lost! " + cause.getMessage());
                        }
                        Snackbar.make(binding.getRoot(), "MQTT Connection lost!", Snackbar.LENGTH_LONG).show();
                    });
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    // Parse the received JSON message
                    String payload = new String(message.getPayload());
                    JSONObject jsonData = new JSONObject(payload);

                    // Extract vehicle ID from the topic
                    String[] topicParts = topic.split("/");
                    final String vehicleId = topicParts.length >= 3 ? topicParts[2] : "unknown";

                    // Extract coordinates and timestamp
                    final double lat = jsonData.getDouble("lat");
                    final double lon = jsonData.getDouble("lon");
                    final String timestamp = jsonData.getString("timestamp");

                    // Extract status (with default value if not present)
                    final String vehicleStatus = jsonData.optString("status", "unknown");

                    // Log the data
                    System.out.println("Received MQTT: " + vehicleId + " at " + lat + ", " + lon + " @ " + timestamp + ", status: " + vehicleStatus);

                    // Update UI on the main thread
                    runOnUiThread(() -> {
                        LatLng location = new LatLng(lat, lon);
                        updateMapWithLocation(location);

                        if (statusTextView != null) {
                            statusTextView.setText("Status: Vehicle " + vehicleId + " updated @ " + timestamp);
                        }

                        // Update status indicator based on vehicle status
                        updateStatusIndicator(vehicleStatus);

                        // If auto-tracking is enabled, make sure the camera follows the vehicle
                        if (isAutoTracking && mMap != null) {
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 15));
                        }
                    });
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Not used for subscription
                }
            });

            // Connect to broker and subscribe
            mqttClient.connect(options);
            mqttClient.subscribe(mqttTopic, 0);
            mqttConnected = true;

            if (statusTextView != null) {
                statusTextView.setText("Status: Connected to MQTT, listening for updates...");
            }

            Snackbar.make(binding.getRoot(), "Connected to MQTT broker", Snackbar.LENGTH_LONG).show();
        } catch (MqttException e) {
            e.printStackTrace();
            if (statusTextView != null) {
                statusTextView.setText("Status: MQTT Error - " + e.getMessage());
            }
            Snackbar.make(binding.getRoot(), "Failed to connect to MQTT: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
        }
    }

    // Method to simulate movement to the next route point
    private void simulateNextRoutePoint() {
        System.out.println("Simulating next route point: " + currentRouteIndex + " of " + routeCoordinates.size());

        if (currentRouteIndex < routeCoordinates.size()) {
            LatLng currentLocation = routeCoordinates.get(currentRouteIndex);
            System.out.println("Current location in simulation: " + currentLocation.latitude + ", " + currentLocation.longitude);

            // Update map
            updateMapWithLocation(currentLocation);

            // Move to next point in the route
            currentRouteIndex++;

            // Schedule the next update
            routeHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    simulateNextRoutePoint();
                }
            }, ROUTE_UPDATE_INTERVAL);

            // Update status
            if (statusTextView != null) {
                statusTextView.setText("Status: Showing route point " + currentRouteIndex + " of " + routeCoordinates.size());
            }
        } else {
            // End of route
            if (statusTextView != null) {
                statusTextView.setText("Status: End of route reached");
            }
        }
    }

    private BitmapDescriptor getBitmapDescriptorFromResource(int resourceId) {
        try {
            // Load bitmap from drawable resources
            Bitmap originalBitmap = BitmapFactory.decodeResource(getResources(), resourceId);

            // Scale the bitmap to an appropriate size for the map (adjust dimensions as needed)
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, 100, 100, false);

            // Create a BitmapDescriptor from the resized bitmap
            return BitmapDescriptorFactory.fromBitmap(resizedBitmap);
        } catch (Exception e) {
            e.printStackTrace();
            // Return default marker if there's an error
            return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE);
        }
    }

    // Method to update the map with a location
    private void updateMapWithLocation(LatLng location) {
        if (mMap == null) {
            System.out.println("Map is null, can't update location");
            return;
        }

        System.out.println("Updating map with location: " + location.latitude + ", " + location.longitude);

        // Clear previous markers
        mMap.clear();

        BitmapDescriptor carIcon = getBitmapDescriptorFromResource(R.raw.car);

        // // Add a marker at current location with custom appearance
        // MarkerOptions markerOptions = new MarkerOptions()
        //     .position(location)
        //     .title("Current Position")
        //     .snippet("Route point " + currentRouteIndex)
        //     .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)) // Use a different color
        //     .zIndex(2.0f); // Place above other markers


        MarkerOptions markerOptions = new MarkerOptions().position(location).title("Current Position").snippet("Route point " + currentRouteIndex).icon(carIcon) // Use custom car icon
                .anchor(0.5f, 0.5f) // Center the icon on the position
                .zIndex(2.0f); // Place above other markers


        mMap.addMarker(markerOptions);

        // Show route trail with reduced marker size
        for (int i = 0; i < currentRouteIndex && i < routeCoordinates.size(); i++) {
            mMap.addMarker(new MarkerOptions().position(routeCoordinates.get(i)).alpha(0.5f) // semi-transparent
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)).anchor(0.5f, 0.5f) // center the marker
                    .visible(true).zIndex(1.0f));
        }

        // Move camera to current location with zoom if auto-tracking is enabled
        if (isAutoTracking) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 15));
        }

        // Move camera to current location with zoom
        // mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 15));

        // Update the info panel
        updateInfoPanel(location);
    }

    // Helper method to update the info panel with LatLng
    private void updateInfoPanel(LatLng location) {
        // if (locationTextView != null) {
        //     locationTextView.setText("Following predefined route");
        // }

        // if (coordinatesTextView != null) {
        //     coordinatesTextView.setText(String.format("Latitude: %.6f, Longitude: %.6f", 
        //             location.latitude, location.longitude));
        // }

        if (locationTextView != null) {
            locationTextView.setText(mqttConnected ? "Live tracking mode" : "Following predefined route");
        }

        if (coordinatesTextView != null) {
            coordinatesTextView.setText(String.format("Latitude: %.6f, Longitude: %.6f", location.latitude, location.longitude));
        }

//        if (statusTextView != null) {
//            statusTextView.setText("Status: Route point " + currentRouteIndex + " of " + routeCoordinates.size());
//        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        System.out.println("MAP IS READY NOW!");

        // Set map UI settings for better user experience
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        // If we have route coordinates, show the first point
        if (!routeCoordinates.isEmpty()) {
            System.out.println("ROUTE COORDINATES ARE NOT EMPTY: " + routeCoordinates.size() + " points loaded");
            LatLng firstPoint = routeCoordinates.get(0);
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(firstPoint, 15));
            System.out.println("DISPLAYING FIRST POINT: " + firstPoint.latitude + ", " + firstPoint.longitude);
            updateMapWithLocation(firstPoint);
        } else {
            System.out.println("ROUTE COORDINATES ARE EMPTY!");
        }

        // Check for location permission (still needed for the map's "My Location" button)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
            System.out.println("LOCATION PERMISSION GRANTED");
        } else {
            System.out.println("REQUESTING LOCATION PERMISSION");
            // Request location permission
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Enable the my-location layer
            mMap.setMyLocationEnabled(true);
        }
    }

    // Keeping this method for backward compatibility, but it will use route data
    private void updateLocationOnMap() {
        startRouteSimulation();
    }

    // Helper method to update the info panel with Location
    private void updateInfoPanel(Location location) {
        updateInfoPanel(new LatLng(location.getLatitude(), location.getLongitude()));
    }

    // Method to request fresh location data - now just advances route
    private void requestNewLocation() {
        startRouteSimulation();
    }

    // Add this new method to update the status indicator
    private void updateStatusIndicator(String status) {
        if (statusIndicatorBox == null || statusIndicatorText == null) return;

        int color;
        String statusText;

        switch (status.toLowerCase()) {
            case "idle":
                color = ContextCompat.getColor(this, android.R.color.holo_green_light);
                statusText = "Car is idle";
                break;
            case "moving":
                color = ContextCompat.getColor(this, android.R.color.holo_orange_light);
                statusText = "Car is moving";
                break;
            case "unauthorized":
                color = ContextCompat.getColor(this, android.R.color.holo_red_light);
                statusText = "Unauthorized moving!";
                break;
            default:
                color = ContextCompat.getColor(this, android.R.color.darker_gray);
                statusText = "Unknown status";
                break;
        }

        statusIndicatorBox.setBackgroundColor(color);
        statusIndicatorText.setText(statusText);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
            } else {
                Snackbar.make(binding.getRoot(), "Location permission is required for full functionality", Snackbar.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop route simulation
        routeHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // We don't automatically restart the simulation here to give the user control
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        try {
            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
            return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp();
        } catch (Exception e) {
            e.printStackTrace();
            return super.onSupportNavigateUp();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }
}