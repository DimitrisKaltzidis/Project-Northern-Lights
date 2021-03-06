package com.jim.robotos_v2.Utilities;

import android.content.Context;
import android.content.res.Resources;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.location.Location;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.jim.robotos_v2.R;
import com.jim.robotos_v2.RouteLogic.Point;
import com.jim.robotos_v2.RouteLogic.Route;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.abs;

/**
 * Created by the awesome and extraordinary developer Jim on 9/12/2015.
 * Prepare yourself BAD English imminent
 */
public class Utilities {

    static float[] mGravity = new float[3];
    static float[] mGeomagnetic = new float[3];
    /**
     * the length of one minute of latitude in meters, i.e. one nautical mile in meters.
     */
    private static final double MINUTES_TO_METERS = 1852d;
    /**
     * the amount of minutes in one degree.
     */
    private static final double DEGREE_TO_MINUTES = 60d;

    // static String previousCommand = "STOP";

    public static float landscapeModeCompassCalibration(SensorEvent event) {

        float azimuth = 0;

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                mGravity = event.values.clone();
                break;

            case Sensor.TYPE_MAGNETIC_FIELD:
                mGeomagnetic = event.values.clone();
                break;
        }

        float R[] = new float[9];
        float I[] = new float[9];
        boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
        if (success)

        {
            float orientation[] = new float[3];
            SensorManager.getOrientation(R, orientation);
            azimuth = orientation[0]; // orientation contains: azimuth, pitch and roll
            azimuth = (float) Math.toDegrees(azimuth) + 90;
            azimuth = (azimuth + 360) % 360;
        }

        return azimuth;
    }

    public static int getMapType(Context context) {
        String mapType = Preferences.loadPrefsString("MAP_TYPE", "HYBRID", context);
        int mapTypeInteger;
        switch (mapType) {

            case "HYBRID":
                mapTypeInteger = GoogleMap.MAP_TYPE_HYBRID;
                break;
            case "NORMAL":
                mapTypeInteger = GoogleMap.MAP_TYPE_NORMAL;
                break;
            case "TERRAIN":
                mapTypeInteger = GoogleMap.MAP_TYPE_TERRAIN;
                break;
            default:
                mapTypeInteger = GoogleMap.MAP_TYPE_HYBRID;
        }
        return mapTypeInteger;
    }

    public static String giveDirection(float compassBearing, ImageView ivDirection, ImageView ivCompass, Route route, Location robotLocation, Context context, String previousCommand, GoogleMap mMap, Resources resources, TextView tvDistance, TextToSpeech tts, Bluetooth bluetooth, boolean obstacleAvoidingClass) {
        String directionToReturn;

        float bearingRange = Preferences.loadPrefsFloat("BEARING_RANGE", 20, context);

        if (route.getNextPoint() != null) {
            float desiredBearing = calculateAngleBetweenTwoPoint(robotLocation, route.getNextPoint().getPositionAsLocationobject(), true);

            desiredBearing = correctBearing(desiredBearing);

            Utilities.decideCompassImage(ivCompass, compassBearing, desiredBearing, bearingRange);

            float distance = robotLocation.distanceTo(route.getNextPoint().getPositionAsLocationobject());

            if (!obstacleAvoidingClass)
                tvDistance.setText((int) distance + "m");

            float distanceErrorRange = Preferences.loadPrefsFloat("DISTANCE_ERROR_RANGE", 3, context);

            if (distance <= distanceErrorRange) {
                tts.speak(route.getNextPoint().getName() + " REACHED", TextToSpeech.QUEUE_ADD, null);
                route.setCurrentPointAsVisited();
                MapUtilities.drawPathOnMap(mMap, route, resources);

                return previousCommand;
            } else {

                if (inRange(compassBearing, desiredBearing, bearingRange)) {
                    directionToReturn = "FORWARD";
                } else {
                    if (desiredBearing >= 180) {
                        double symmetricBearing = desiredBearing - 180;
                        if (compassBearing < desiredBearing && compassBearing > symmetricBearing) {
                            directionToReturn = "RIGHT";
                        } else {
                            directionToReturn = "LEFT";
                        }
                    } else {
                        double symmetricBearing = desiredBearing + 180;
                        if (compassBearing > desiredBearing && compassBearing < symmetricBearing) {
                            directionToReturn = "LEFT";
                        } else {
                            directionToReturn = "RIGHT";
                        }
                    }
                }
                if (!directionToReturn.equals(previousCommand))
                    setDirectionImage(directionToReturn, ivDirection, bluetooth);

                return directionToReturn;
            }
        } else {
            if (!tts.isSpeaking()) {
                tts.speak("ROUTE COMPLETED", TextToSpeech.QUEUE_ADD, null);
            }
            return "FINISH";

        }

    }

    public static float calculateAngleBetweenTwoPoint(Location myLocation, Location destinationLocation, boolean automaticMode) {
        if (automaticMode) {
            return myLocation.bearingTo(destinationLocation);
        } else {
            return (float) angleFromCoordinate(new LatLng(myLocation.getLatitude(), myLocation.getLongitude()), new LatLng(destinationLocation.getLatitude(), destinationLocation.getLongitude()));
        }
    }

    public static void setDirectionImage(String command, ImageView ivDirection, Bluetooth bluetooth) {

        int directionDrawable;
        int strCmndToInt = -1;
        switch (command) {
            case "FORWARD":
                directionDrawable = R.drawable.forward;
                strCmndToInt = 0;
                break;
            case "LEFT":
                directionDrawable = R.drawable.rotate_left;
                strCmndToInt = 1;
                break;
            case "RIGHT":
                directionDrawable = R.drawable.rotate_right;
                strCmndToInt = 2;
                break;
            case "BACKWARD":
                directionDrawable = R.drawable.backward;
                strCmndToInt = 4;
                break;
            case "STOP":
                directionDrawable = R.drawable.stop;
                strCmndToInt = 3;
                break;
            default:
                directionDrawable = R.drawable.stop;
                strCmndToInt = 3;

        }


   /*     Log.d("COMMAND SEND", "previous:" + previousCommand + " current:" + command);
        if (!command.equals(previousCommand)) {*/
        try {
            bluetooth.sendMessage(Integer.toString(strCmndToInt));
            ivDirection.setImageResource(directionDrawable);
            // previousCommand = command;
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("DIRECTIONS", "COULD NOT SET DIRECTION IMAGE");
            Log.e("BLUETOOTH", "COULD NOT SEND DIRECTION TO ROBOT");
        }
        // }
    }

    public static boolean inRange(float compassBearing, float desiredBearing, float bearingRange) {

        boolean adjusted = false;
        boolean inRange = false;

        double upLimit = (double) desiredBearing + bearingRange;
        double lowLimit = (double) desiredBearing - bearingRange;
        double compassBearingFloat = (double) compassBearing;

        if (upLimit > 360 || upLimit < 0) {
            adjusted = true;
        }

        if (lowLimit > 360 || lowLimit < 0) {
            adjusted = true;
        }

        upLimit = normalizeAngles(upLimit);
        lowLimit = normalizeAngles(lowLimit);

        if (adjusted) {
            if (compassBearingFloat >= lowLimit || compassBearingFloat < upLimit) {
                inRange = true;
            }
        } else {
            if (compassBearingFloat >= lowLimit && compassBearingFloat < upLimit) {
                inRange = true;
            }
        }
        return inRange;
    }

    public static double normalizeAngles(double angle) {
        if (angle >= 0)
            angle = (angle % 360);
        else
            angle = ((angle % 360) + 360) % 360;

        return angle;
    }

    public static float correctBearing(float bearing) {
        if (bearing < 0) {
            return bearing + 360;
        }
        return bearing;
    }

    public static float correctCompassBearing(float compassBearingDegreesFromSensor, Location robotsLocation) {

        GeomagneticField geoField = new GeomagneticField(Double
                .valueOf(robotsLocation.getLatitude()).floatValue(), Double
                .valueOf(robotsLocation.getLongitude()).floatValue(),
                Double.valueOf(robotsLocation.getAltitude()).floatValue(),
                System.currentTimeMillis());
        compassBearingDegreesFromSensor += geoField.getDeclination(); // converts magnetic north into true north

        //Correct the azimuth mirror degrees
        return (float) normalizeAngles((double) compassBearingDegreesFromSensor);
        //return compassBearingDegreesFromSensor % 360;
    }

    public static void decideCompassImage(ImageView ivCompass, float compassBearing, float desiredBearing, float bearingRange) {
        if (Utilities.inRange(compassBearing, desiredBearing, bearingRange)) {
            ivCompass.setImageResource(R.drawable.correct_direction);
        } else {
            ivCompass.setImageResource(R.drawable.wrong_direction);
        }
    }


    public static float compassAnimationHandler(ImageView ivCompass, float compassBearingDegrees, float currentDegree) {

        RotateAnimation rotateAnimation = new RotateAnimation(
                -currentDegree,
                compassBearingDegrees,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF,
                0.5f);

        rotateAnimation.setDuration(210);
        ivCompass.startAnimation(rotateAnimation);
        currentDegree = -compassBearingDegrees;
        return currentDegree;
    }

    public static float compassNorthIconHandler(ImageView ivCompassNorth, float compassBearingDegrees, float currentDegreeNorth) {
        RotateAnimation rotateAnimation = new RotateAnimation(
                currentDegreeNorth,
                -compassBearingDegrees,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF,
                0.5f);

        rotateAnimation.setDuration(210);
        ivCompassNorth.startAnimation(rotateAnimation);
        currentDegreeNorth = -compassBearingDegrees;
        return currentDegreeNorth;
    }

    public static LocationRequest createLocationRequest(Resources res) {
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(res.getInteger(R.integer.refresh_location_interval_milliseconds));
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return mLocationRequest;
    }

    /**
     * Calculates the angle between two points.
     *
     * @param point1 first point for angle calculation
     * @param point2 second point for angle calculation
     * @return a double number representing an angle
     */
    public static double angleFromCoordinate(LatLng point1, LatLng point2) {

        double lat1 = point1.latitude;
        double long1 = point1.longitude;
        double lat2 = point2.latitude;
        double long2 = point2.longitude;

        double dLon = (long2 - long1);

        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1)
                * Math.cos(lat2) * Math.cos(dLon);

        double brng = Math.atan2(y, x);

        brng = Math.toDegrees(brng);
        brng = (brng + 360) % 360;
        brng = 360 - brng;

        return abs(brng - 360);
    }

    public static LatLng convertLocationToLatLng(Location location) {
        return new LatLng(location.getLatitude(), location.getLongitude());
    }

    public static Location convertLatLngToLocation(LatLng latLng) {
        Location temp = new Location("");
        temp.setLatitude(latLng.latitude);
        temp.setLongitude(latLng.longitude);
        return temp;
    }

    public static boolean playStopButtonHandler(Route route, boolean running, ImageView ivPlayStop, Context context) {
        if (!route.isEmpty()) {
            if (running) {
                running = false;
                ivPlayStop.setImageResource(R.drawable.play);
            } else {
                running = true;
                ivPlayStop.setImageResource(R.drawable.pause);
            }
        } else {
            Toast.makeText(context, "Please define a route", Toast.LENGTH_SHORT).show();
        }
        return running;
    }

    public static List<LatLng> decodePoly(String encoded) {

        List<LatLng> poly = new ArrayList<LatLng>();
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

    public static Route convertPathToRoute(String result, Route route) {

        try {
            // Tranform the string into a json object
            final JSONObject json = new JSONObject(result);
            JSONArray routeArray = json.getJSONArray("routes");
            JSONObject routes = routeArray.getJSONObject(0);
            JSONObject overviewPolylines = routes
                    .getJSONObject("overview_polyline");
            String encodedString = overviewPolylines.getString("points");
            List<LatLng> list = decodePoly(encodedString);

            for (LatLng latLng : list) {
                route.addPoint(new Point(latLng, "Point " + route.getPointsNumber(), false));
            }

        } catch (JSONException e) {
            e.printStackTrace();
            Log.e("Utilities", "drawPath: ");
        }

        return route;
    }

    public static String makeURL(LatLng start, LatLng finish) {
        double sourceLat, sourceLong, destLat, destLog;
        sourceLat = start.latitude;
        sourceLong = start.longitude;
        destLat = finish.latitude;
        destLog = finish.longitude;

        StringBuilder urlString = new StringBuilder();
        urlString.append("http://maps.googleapis.com/maps/api/directions/json");
        urlString.append("?origin=");// from
        urlString.append(Double.toString(sourceLat));
        urlString.append(",");
        urlString.append(Double.toString(sourceLong));
        urlString.append("&destination=");// to
        urlString.append(Double.toString(destLat));
        urlString.append(",");
        urlString.append(Double.toString(destLog));
        urlString.append("&sensor=false&mode=driving&alternatives=true");
        return urlString.toString();
    }

    public static Scalar convertScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }

    public static Scalar convertScalarRgba2Hsv(Scalar rgbColor) {
        Mat pointMatHsv = new Mat();
        Mat pointMatRgba = new Mat(1, 1, CvType.CV_8UC3, rgbColor);
        Imgproc.cvtColor(pointMatRgba, pointMatHsv, Imgproc.COLOR_RGB2HSV_FULL, 4);

        return new Scalar(pointMatHsv.get(0, 0));
    }


    /**
     * The method that gives commands to the robot while the app is in Color Detection Mode
     *
     * @param center           the center of the detecting color
     * @param distanceToObject the distance to the object
     * @param bottomLineHeight the height of line that determines if we reached the detected color object
     * @param leftLineWidth    the left line is the left limit of the area to which if the center is in the command is FORWARD
     * @param rightLineWidth   the right line is the right limit of the area to which if the center is in the command is FORWARD
     * @param ivDirection      ImageView to set command on the UI
     * @param bt               bluetooth object to handle the communication
     * @param context          the application context to access preferences
     */
    public static String giveDirectionColorDetectionVersion2(org.opencv.core.Point center, int distanceToObject, double bottomLineHeight, double leftLineWidth, double rightLineWidth, ImageView ivDirection, Bluetooth bt, Context context, String previousCommand, boolean inScenarioMode) {

        String command = "STOP";
        boolean finished = false;
        try {


            if (distanceToObject < Preferences.loadPrefsInt("DISTANCE_TO_STOP_FROM_OBSTACLE_CM", 50, context)) {
                command = "STOP";
                if (inScenarioMode)
                    finished = true;
            } else {
                if (center.y != -1) {
                    if (center.y > bottomLineHeight) {
                        command = "STOP";
                        if (inScenarioMode)
                            finished = true;
                    } else {
                        if (center.x >= leftLineWidth && center.x <= rightLineWidth) {
                            command = "FORWARD";
                        } else if (center.x < leftLineWidth) {
                            command = "RIGHT";
                        } else {
                            command = "LEFT";
                        }
                    }
                } else {
                    command = "RIGHT";
                }
                // Log.d("pigadi", center.y + "");
            }

            if (!command.equals(previousCommand))
                Utilities.setDirectionImage(command, ivDirection, bt);

        } catch (Exception e) {
            e.printStackTrace();
            Utilities.setDirectionImage("STOP", ivDirection, bt);
            command = "STOP";
        }

        if (finished) {
            return "FINISH";
        }
        return command;


    }

    static int counter = 0;
    static float SmoothFactorCompass = 0.5f;
    static float SmoothThresholdCompass = 30.0f;

    /**
     * Eliminates the spikes (glitches) of the robot's proximity sensor based on min difference value(kai kala)
     *
     * @param newCompass the current sensor reading
     * @param oldCompass the previous sensor reading
     * @return the value with no spikes
     */
    public static int normalizeReadingsFromDistanceSensor(int newCompass, int oldCompass) {

        if (Math.abs(newCompass - oldCompass) < 180) {
            if (Math.abs(newCompass - oldCompass) > SmoothThresholdCompass) {
                oldCompass = newCompass;
            } else {
                oldCompass = (int) (oldCompass + SmoothFactorCompass * (newCompass - oldCompass));
            }
        } else {
            if (360.0 - Math.abs(newCompass - oldCompass) > SmoothThresholdCompass) {
                oldCompass = newCompass;
            } else {
                if (oldCompass > newCompass) {
                    oldCompass = (int) ((oldCompass + SmoothFactorCompass * ((360 + newCompass - oldCompass) % 360) + 360) % 360);
                } else {
                    oldCompass = (int) ((oldCompass - SmoothFactorCompass * ((360 - newCompass + oldCompass) % 360) + 360) % 360);
                }
            }
        }
        return oldCompass;
    }

    public static double applyLowPassFilter() {
        return 0;
    }

    /**
     * Calculates the Euclid distance between two points in Cartesian like surface
     *
     * @param pointA the first point
     * @param pointB the second point
     * @return the distance as float
     */
    public static float calculateDistanceBetweenTwoPoints(org.opencv.core.Point pointA, org.opencv.core.Point pointB) {
        return (float) Math.sqrt(Math.pow(pointA.x - pointB.x, 2) + Math.pow(pointA.y - pointB.y, 2));
    }


    /**
     * Send a command to the robot if the current command is different than the previous; to avoid lag on communication
     *
     * @param ivDirection     ImageView to set command on the UI
     * @param bt              bluetooth object to handle the communication
     * @param command         the command for the robot
     * @param previousCommand the previous command for the robot
     */
    public static void giveDirectionObstacleAvoidance(ImageView ivDirection, Bluetooth bt, String command, String previousCommand) {
        if (!command.equals(previousCommand))
            setDirectionImage(command, ivDirection, bt);
    }

    /**
     * Calculates and creates a Point to avoid the obstacle
     *
     * @param obstacleAvoidanceDegrees the degrees to avoid the object
     * @param obstacleCompassDegrees   the degrees the obstacle was detected
     * @param distanceToObstacle       the distance between the robot and the obstacle
     * @param robotLocation            the location of the robot (coordinates as Location object)
     * @param errorRange               error range to consider a obstacle visited
     * @param context                  the application context to access preferences
     * @return the Point towards to i must navigate to avoid the obstacle
     */
    public static Point calculateObstacleAvoidingPoint(float obstacleAvoidanceDegrees, float obstacleCompassDegrees, int distanceToObstacle, Location robotLocation, float errorRange, Context context) {

        //Obstacle-Robot-Point angle
        float orp = 180 - abs(abs(obstacleCompassDegrees - obstacleAvoidanceDegrees) - 180);

        //Obstacle-Point-Robot angle
        float opr = 90 - orp;

        //Ration between the two opposite angles
        float angleRatio = (orp / opr);

        //Obstacle-Point Length
        float op = angleRatio * distanceToObstacle;

        //Hypotenuse Length
        float hypotenuse = (float) Math.sqrt((double) Math.pow(distanceToObstacle, 2) + Math.pow(op, 2));

        //Calculate avoiding point coordinates based on angle (might be proven faulty)
        LatLng avoidingPointLatLng = extrapolate(robotLocation, (double) obstacleAvoidanceDegrees /*+ Preferences.loadPrefsFloat("OBSTACLE_AVOIDING_BEARING_ERROR_RANGE_DEGREES", 3f, context)*/, (double) (hypotenuse / 100) + errorRange);

        return new Point(avoidingPointLatLng, "Obstacle avoiding point", true);
    }

    /**
     * Calculates the coordinates of a new point based on given Point Coordinates the distance between them in meters and the angle the user stares
     *
     * @param startingLatLngPoint the starting point
     * @param course              the angle representing the course we want to calculate the new point to
     * @param distance            the meters towards that course
     * @return the new Point coordinates as LatLng object
     */
    public static LatLng extrapolate(Location startingLatLngPoint, final double course,
                                     final double distance) {

        double startPointLat = startingLatLngPoint.getLatitude();
        double startPointLon = startingLatLngPoint.getLongitude();
        final double crs = Math.toRadians(course);
        final double d12 = Math.toRadians(distance / MINUTES_TO_METERS / DEGREE_TO_MINUTES);

        final double lat1 = Math.toRadians(startPointLat);
        final double lon1 = Math.toRadians(startPointLon);

        final double lat = Math.asin(Math.sin(lat1) * Math.cos(d12)
                + Math.cos(lat1) * Math.sin(d12) * Math.cos(crs));
        final double dlon = Math.atan2(Math.sin(crs) * Math.sin(d12) * Math.cos(lat1),
                Math.cos(d12) - Math.sin(lat1) * Math.sin(lat));
        final double lon = (lon1 + dlon + Math.PI) % (2 * Math.PI) - Math.PI;

        return new LatLng(Math.toDegrees(lat), Math.toDegrees(lon));
    }


    /**
     * Calculates the coordinates of a new point based on given Point Coordinates and the x,y offset in meters
     *
     * @param currentPoint the starting point
     * @param xOffset      offset in the X axis of the earth
     * @param yOffset      offset in the Y axis of the earth
     * @return the new Point coordinates as LatLng object
     */
    public static LatLng calculateNewLatLongPointGivenOffsetInMeters(LatLng currentPoint, int xOffset, int yOffset) {
        //Position, decimal degrees
        double lat = currentPoint.latitude;
        double lon = currentPoint.longitude;

        Log.e("OLD POINT", "LATITUDE: " + lat + " LONGITUDE: " + lon);

        //Earth’s radius, sphere
        int R = 6378137;

        //offsets in meters
        int dn = xOffset;
        int de = yOffset;

        //Coordinate offsets in radians
        double dLat = dn / R;
        double dLon = de / (R * Math.cos(Math.PI * lat / 180));

        //OffsetPosition, decimal degrees
        double latO = lat + dLat * (180 / Math.PI);
        double lonO = lon + dLon * (180 / Math.PI);

        Log.e("NEW POINT", "LATITUDE: " + latO + " LONGITUDE: " + lonO);

        return new LatLng(latO, lonO);
    }

}
