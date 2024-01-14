package com.example.login;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Startseite extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    FirebaseAuth auth;
    FirebaseUser user;
    GoogleMap gMap;
    Marker currentLocationMarker;
    FloatingActionButton searchButton;
    SearchView searchView;
    List<Marker> allMarkers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startseite);
        auth = FirebaseAuth.getInstance();
        searchButton = findViewById(R.id.search);
        searchView = findViewById(R.id.searchView);


        user = auth.getCurrentUser();
        if (user == null) {
            Intent intent = new Intent(getApplicationContext(), Login.class);
            startActivity(intent);
            finish();
        } else {
            String cachedUserId = UserCache.getInstance().getUserId();
            if (cachedUserId != null && cachedUserId.equals(user.getUid())) {
                Intent intent = new Intent(getApplicationContext(), ManageRestaurant.class);
                startActivity(intent);
                overridePendingTransition(0, 0);
                finish();
            } else {
                checkUserInDatabase(user.getUid());
            }
        }

        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        String employeeKey = sharedPreferences.getString("employeeKey", null);

        if (employeeKey != null) {
            Intent intent = new Intent(getApplicationContext(), EmployeesView.class);
            startActivity(intent);
            finish();
        }

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);
        NavigationManager.setupBottomNavigationView(bottomNavigationView, this);

        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String searchText = searchView.getQuery().toString();
                searchRestaurants(searchText);
            }
        });

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (GoogleMap.MAP_TYPE_NORMAL == gMap.getMapType()) {
                    gMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                } else {
                    gMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                }
            }
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        DropdownManager dropdownManager = new DropdownManager(this, R.menu.dropdown_menu, R.id.imageMenu);
        dropdownManager.setupDropdown();
    }

    private void searchRestaurants(String searchText) {
        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("Restaurants");
        dbRef.orderByChild("daten/name").equalTo(searchText).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String restaurantName = snapshot.child("daten/name").getValue(String.class);

                    for (Marker marker : allMarkers) {
                        if (marker.getTitle().equals(restaurantName)) {
                            gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 15));
                            break;
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }    //mmm
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);

        if (bottomNavigationView.getSelectedItemId() != R.id.menuHome) {
            bottomNavigationView.setSelectedItemId(R.id.menuHome);
        }
    }

    public String getUserId() {
        auth = FirebaseAuth.getInstance();
        user = auth.getCurrentUser();
        return user.getUid();
    }

    private void checkUserInDatabase(String uid) {
        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("Restaurants");
        dbRef.orderByChild("daten/uid").equalTo(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    UserCache.getInstance().setUserId(uid);
                    Intent intent = new Intent(getApplicationContext(), ManageRestaurant.class);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        SupportMapFragment mMapView = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        gMap = googleMap;
        gMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        MyLocationListener locationListener = new MyLocationListener(this, gMap, this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
            locationListener.getLastKnownLocation(locationManager);
        }
        addRestaurantsOnMap();
        int padding = 100; // replace with desired padding in pixels
        gMap.setPadding(0, padding, 0, 0);


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onMapReady(gMap);
            } else {
                Toast.makeText(this, "Bastard", Toast.LENGTH_LONG).show();
            }
        }
    }

    public void addRestaurantsOnMap() {
        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference("Restaurants");
        dbRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (DataSnapshot restaurantSnapshot : dataSnapshot.getChildren()) {
                        Restaurant restaurant = restaurantSnapshot.getValue(Restaurant.class);
                        Daten daten = restaurant.getDaten();
                        String address = daten.getStrasse() + " " + daten.getHausnr() + ", " + daten.getPlz() + " " + daten.getOrt();
                        LatLng latLng = getLatLngFromAddress(address);
                        if (latLng != null && currentLocationMarker != null) {
                            float[] results = new float[1];
                            Location.distanceBetween(currentLocationMarker.getPosition().latitude, currentLocationMarker.getPosition().longitude,
                                    latLng.latitude, latLng.longitude, results);
                            int distanceInMeters = (int) results[0];
                            Marker marker = gMap.addMarker(new MarkerOptions()
                                    .position(latLng)
                                    .title(daten.getName())
                                    .snippet("Entfernung: " + distanceInMeters / 1000 + "km"));
                            allMarkers.add(marker);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });
    }

    public LatLng getLatLngFromAddress(String address) {
        Geocoder geocoder = new Geocoder(this);
        List<Address> addresses;
        LatLng latLng = null;

        try {
            addresses = geocoder.getFromLocationName(address, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address returnedAddress = addresses.get(0);
                latLng = new LatLng(returnedAddress.getLatitude(), returnedAddress.getLongitude());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return latLng;
    }
}