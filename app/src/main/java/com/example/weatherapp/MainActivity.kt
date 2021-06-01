package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.TimeZone
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private var mProgressDialog: Dialog? = null

    private lateinit var mSharedPreferences: SharedPreferences

    //initialise widgets

    private lateinit var iv_main:ImageView
    private lateinit var iv_humidity:ImageView
    private lateinit var iv_min_max:ImageView
    private lateinit var iv_wind:ImageView
    private lateinit var iv_location:ImageView
    private lateinit var iv_sunrise:ImageView
    private lateinit var iv_sunset:ImageView

    private lateinit var tv_main: TextView
    private lateinit var tv_main_description: TextView
    private lateinit var tv_temp: TextView
    private lateinit var tv_humidity: TextView
    private lateinit var tv_min: TextView
    private lateinit var tv_max: TextView
    private lateinit var tv_speed: TextView
    private lateinit var tv_speed_unit: TextView
    private lateinit var tv_name: TextView
    private lateinit var tv_country: TextView
    private lateinit var tv_sunrise_time: TextView
    private lateinit var tv_sunset_time: TextView

    private var mLatitude: Double = 0.0

    private var mLongitude: Double = 0.0



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        //Initialise widgets

        iv_main=findViewById(R.id.iv_main)
        iv_humidity=findViewById(R.id.iv_humidity)
        iv_min_max=findViewById(R.id.iv_min_max)
        iv_wind=findViewById(R.id.iv_wind)
        iv_location=findViewById(R.id.iv_location)
        iv_sunrise=findViewById(R.id.iv_sunrise)
        iv_sunset=findViewById(R.id.iv_sunset)


        tv_main=findViewById(R.id.tv_main)
        tv_country=findViewById(R.id.tv_country)
        tv_humidity=findViewById(R.id.tv_humidity)
        tv_main_description=findViewById(R.id.tv_main_description)
        tv_max=findViewById(R.id.tv_max)
        tv_min=findViewById(R.id.tv_min)
        tv_name=findViewById(R.id.tv_name)
        tv_speed=findViewById(R.id.tv_speed)
        tv_speed_unit=findViewById(R.id.tv_speed_unit)
        tv_sunrise_time=findViewById(R.id.tv_sunrise_time)
        tv_sunset_time=findViewById(R.id.tv_sunset_time)
        tv_temp=findViewById(R.id.tv_temp)


        mSharedPreferences=getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        mFusedLocationClient=LocationServices.getFusedLocationProviderClient(this)

        setUpUI()

        if(!isLocationEnabled()){
            Toast.makeText(this,
                "Location service is turned off. Please turn it on.",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withActivity(this)
                .withPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                )
                .withListener(object: MultiplePermissionsListener{
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if(report!!.areAllPermissionsGranted()){
                            requestLocationData()
                        }
                        if(report!!.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(this@MainActivity,
                                "You have denied permission to access location. Please enable to work with the app",
                                Toast.LENGTH_SHORT)
                                .show()
                        }

                    }

                    override fun onPermissionRationaleShouldBeShown(
                        p0: MutableList<PermissionRequest>?,
                        p1: PermissionToken?
                    ) {
                        showRationaleDialogForPermission()
                    }

                }).onSameThread().check()
        }


    }

    private fun showRationaleDialogForPermission(){
        AlertDialog.Builder(this)
            .setMessage("It looks like permission for location is turned off. You need to turn it on.")
            .setPositiveButton("Go To Settings"){
                _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri= Uri.fromParts("package", packageName, null)
                    intent.data=uri
                    startActivity(intent)
                }catch (e: ActivityNotFoundException){e.printStackTrace()}
            }
            .setNegativeButton("Cancel"){
                dialog, _ ->
                dialog.dismiss()
            }.show()
    }


    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val mLocationRequest= LocationRequest()
        mLocationRequest.priority=LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallBack, Looper.myLooper()
        )
    }

    private val mLocationCallBack= object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            //super.onLocationResult(locationResult)

            val mLastLocation: Location = locationResult.lastLocation
            mLatitude= mLastLocation.latitude
            //Log.d("CURRENT LATITUDE", "$latitude")

            mLongitude = mLastLocation.longitude
            //Log.d("CURRENT LONGITUDE", "$longitude")

            getLocationWeatherDetails()

        }
    }


    private fun getLocationWeatherDetails(){
        if(Constants.isNetworkAvailable(this@MainActivity)){

            //Log.i("Checking internet", "Working")
            //Toast.makeText(this, "Entered", Toast.LENGTH_LONG).show()
            val retrofit: Retrofit= Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service: WeatherService =
                retrofit.create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                mLatitude, mLongitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showCustomProgressDialog()

            listCall.enqueue(object: Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if(response!!.isSuccessful){
                        hideProgressDialog()
                        val weatherList: WeatherResponse? =response.body()

                        val weatherResponseJsonString= Gson().toJson(weatherList)
                        val editor=mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setUpUI()

                    Log.i("RESPONSE RESULT", "$weatherList")
                    }else{
                        val rc=response.code()
                        when(rc){
                            400-> {Log.e("Error 400", "Bad connection to internet")}
                            404 ->{Log.e("Error 404", "Page not found")}
                            else -> {Log.e("Error", "Generic error")}
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    hideProgressDialog()
                    Log.i("Errorrr", t!!.message.toString())
                }

            })

        }else{
            Toast.makeText(this@MainActivity,
                "No internet connection",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    private fun isLocationEnabled(): Boolean{
        val locationManager: LocationManager= getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }


    private fun showCustomProgressDialog(){
        mProgressDialog= Dialog(this)

        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when(item.itemId){
            R.id.refresh_icon-> {
                getLocationWeatherDetails()
                true
            }else->return super.onOptionsItemSelected(item)
        }
    }

    private fun hideProgressDialog(){
        if (mProgressDialog!=null)
        {
            mProgressDialog!!.dismiss()
        }
    }


    @SuppressLint("NewApi")
    private fun setUpUI(){

        val weatherResponseJsonString=mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")

        if(!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
            for(i in weatherList.weather.indices){
                //Log.i("Weather Name", weatherList.weather.toString())

                tv_main.text=weatherList.weather[i].main
                tv_main_description.text = weatherList.weather[i].description
                tv_temp.text =
                    weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                tv_humidity.text = weatherList.main.humidity.toString() + " per cent"
                tv_min.text = weatherList.main.temp_min.toString() + " min"
                tv_max.text = weatherList.main.temp_max.toString() + " max"
                tv_speed.text = weatherList.wind.speed.toString()
                tv_name.text = weatherList.name
                tv_country.text = weatherList.sys.country
                tv_sunrise_time.text = unixTime(weatherList.sys.sunrise)
                //Log.i("Sunrise Time", "${weatherList.sys.sunrise}")
                tv_sunset_time.text = unixTime(weatherList.sys.sunset)

                when (weatherList.weather[i].icon) {
                    "01d" -> iv_main.setImageResource(R.drawable.sunny)
                    "02d" -> iv_main.setImageResource(R.drawable.cloud)
                    "03d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10d" -> iv_main.setImageResource(R.drawable.rain)
                    "11d" -> iv_main.setImageResource(R.drawable.storm)
                    "13d" -> iv_main.setImageResource(R.drawable.snowflake)
                    "01n" -> iv_main.setImageResource(R.drawable.cloud)
                    "02n" -> iv_main.setImageResource(R.drawable.cloud)
                    "03n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10n" -> iv_main.setImageResource(R.drawable.cloud)
                    "11n" -> iv_main.setImageResource(R.drawable.rain)
                    "13n" -> iv_main.setImageResource(R.drawable.snowflake)
                }
            }
        }

    }

    private fun getUnit(value: String): String?{
        var result= "°C"
        if(value == "US" || value=="LR" || value=="MM")
            result="°F"
        return result
    }

    private fun unixTime(timex: Long): String?{
        val date = Date(timex*1000L)
        val sdf = SimpleDateFormat("hh:mm aa", Locale.getDefault())
        sdf.timeZone= TimeZone.getDefault()
        return sdf.format(date)
    }

}