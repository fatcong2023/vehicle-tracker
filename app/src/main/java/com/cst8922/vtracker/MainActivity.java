package com.cst8922.vtracker;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

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
import com.google.android.gms.tasks.OnSuccessListener; // Explicitly import OnSuccessListener
import com.google.android.material.snackbar.Snackbar;

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
            locationTextView = findViewById(R.id.location_text);
            coordinatesTextView = findViewById(R.id.coordinates_text);
            statusTextView = findViewById(R.id.status_text);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Initialize the fusedLocationClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        // Get the SupportMapFragment and request notification when the map is ready
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Update location when FAB is clicked
                updateLocationOnMap();
                Snackbar.make(view, "Updating your location...", Snackbar.LENGTH_LONG)
                        .setAnchorView(R.id.fab)
                        .setAction("Action", null).show();
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Set map UI settings for better user experience
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        
        // Check for location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
        } else {
            // Request location permission
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, 
                                 Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }
    
    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            // Enable the my-location layer
            mMap.setMyLocationEnabled(true);
            
            // Immediately try to get and show current location
            updateLocationOnMap();
        }
    }
    
    private void updateLocationOnMap() {
        if (mMap == null) return;
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                
            // Update status text if available
            if (statusTextView != null) {
                statusTextView.setText("Status: Finding your location...");
            }
            
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        // This is correct now with the proper import
                        public void onSuccess(Location location) {
                            if (location != null) {
                                LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                                
                                // Clear previous markers
                                mMap.clear();
                                
                                // Add a marker at current location
                                MarkerOptions markerOptions = new MarkerOptions()
                                        .position(currentLocation)
                                        .title("You are here")
                                        .snippet("Your current location");
                                
                                mMap.addMarker(markerOptions);
                                
                                // Move camera to current location with zoom
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));
                                
                                // Update the info panel
                                updateInfoPanel(location);
                            } else {
                                // If location is null, show an error and request a location update
                                if (statusTextView != null) {
                                    statusTextView.setText("Status: Could not get location");
                                }
                                
                                // Try requesting a new location instead of using last known
                                requestNewLocation();
                            }
                        }
                    })
                    .addOnFailureListener(this, new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            if (statusTextView != null) {
                                statusTextView.setText("Status: Error - " + e.getMessage());
                            }
                            Snackbar.make(binding.getRoot(), 
                                "Location error: " + e.getMessage(), 
                                Snackbar.LENGTH_LONG).show();
                        }
                    });
        }
    }
    
    // Helper method to update the info panel
    private void updateInfoPanel(Location location) {
        if (locationTextView != null) {
            locationTextView.setText("Current location detected");
        }
        
        if (coordinatesTextView != null) {
            coordinatesTextView.setText(String.format("Latitude: %.6f, Longitude: %.6f", 
                    location.getLatitude(), location.getLongitude()));
        }
        
        if (statusTextView != null) {
            statusTextView.setText("Status: Location found");
        }
    }

    // Method to request fresh location data
    private void requestNewLocation() {
        startLocationUpdates();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
            } else {
                Snackbar.make(binding.getRoot(), "Location permission is required to show your position", 
                        Snackbar.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
        // Code to execute when the app is moved to background
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Code to execute when returning to the app
        if (mMap != null) {
            startLocationUpdates();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        createLocationRequest();
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
            return NavigationUI.navigateUp(navController, appBarConfiguration)
                    || super.onSupportNavigateUp();
        } catch (Exception e) {
            e.printStackTrace();
            return super.onSupportNavigateUp();
        }
    }

    // Setup the location request (call this in onCreate)
    private void createLocationRequest() {
        locationRequest = LocationRequest.create()
                .setInterval(10000) // Update every 10 seconds
                .setFastestInterval(5000) // But not faster than every 5 seconds
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        // Create the callback that will handle location updates
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                // Get the most recent location
                Location location = locationResult.getLastLocation();

                // Update the map with the new location
                if (location != null) {
                    LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());

                    // Clear previous markers
                    if (mMap != null) {
                        mMap.clear();

                        // Add a marker at current location
                        MarkerOptions markerOptions = new MarkerOptions()
                                .position(currentLocation)
                                .title("You are here")
                                .snippet("Your current location");

                        mMap.addMarker(markerOptions);

                        // Move camera to current location with zoom
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));

                        // Update the info panel
                        updateInfoPanel(location);

                        // Update status
                        if (statusTextView != null) {
                            statusTextView.setText("Status: Location updated");
                        }
                    }
                }
            }
        };
    }



    // Method to start location updates
    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                
            if (statusTextView != null) {
                statusTextView.setText("Status: Finding your location...");
            }
            
            fusedLocationClient.requestLocationUpdates(locationRequest,
                    locationCallback,
                    Looper.getMainLooper())
                    .addOnFailureListener(this, new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            if (statusTextView != null) {
                                statusTextView.setText("Status: Error - " + e.getMessage());
                            }
                            Snackbar.make(binding.getRoot(), 
                                "Location error: " + e.getMessage(), 
                                Snackbar.LENGTH_LONG).show();
                        }
                    });
        }
    }

    // Method to stop location updates (call in onPause)
    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

}